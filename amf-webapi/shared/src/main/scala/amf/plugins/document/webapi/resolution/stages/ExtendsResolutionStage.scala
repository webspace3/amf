package amf.plugins.document.webapi.resolution.stages

import amf.ProfileNames
import amf.core.annotations.Aliases
import amf.core.emitter.SpecOrdering
import amf.core.metamodel.domain.DomainElementModel
import amf.core.model.document.{BaseUnit, DeclaresModel, Fragment, Module}
import amf.core.model.domain.{DataNode, DomainElement, NamedDomainElement}
import amf.core.parser.ParserContext
import amf.core.resolution.stages.{ResolutionStage, ResolvedNamedEntity}
import amf.core.unsafe.PlatformSecrets
import amf.plugins.document.webapi.contexts.{Raml08WebApiContext, Raml10WebApiContext, RamlWebApiContext}
import amf.plugins.document.webapi.parser.spec.declaration.DataNodeEmitter
import amf.plugins.domain.webapi.models.templates.{ParametrizedResourceType, ParametrizedTrait, ResourceType, Trait}
import amf.plugins.domain.webapi.models.{EndPoint, Operation}
import amf.plugins.domain.webapi.resolution.stages.DomainElementMerging
import amf.plugins.domain.webapi.resolution.stages.DomainElementMerging._
import org.yaml.model.{YDocument, YMap}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * 1) Get a queue of resource types for this Endpoint.
  * 2) Resolve each resource type and merge each one to the endpoint. Start with the closest to the endpoint.
  * 3) Get the traits as branches, as described in the spec, to get the order of the traits to apply.
  * 4) Resolve each trait and merge each one to the operation in the provided order..
  * 5) Remove 'extends' property from the endpoint and from the operations.
  */
class ExtendsResolutionStage(profile: String, val removeFromModel: Boolean = true)
    extends ResolutionStage(profile)
    with PlatformSecrets {

  /** Default to raml10 context. */
  implicit val ctx: RamlWebApiContext = profile match {
    case ProfileNames.RAML08 => new Raml08WebApiContext(ParserContext())
    case _                   => new Raml10WebApiContext(ParserContext())
  }

  override def resolve(model: BaseUnit): BaseUnit = model.transform(findExtendsPredicate, transform(model))

  def declarations(model: BaseUnit): Unit = {
    model match {
      case d: DeclaresModel => d.declares.foreach { declaration =>
        ctx.declarations += declaration
        processDeclaration(declaration, ctx, model)
      }
      case _                =>
    }
    nestedDeclarations(model)
  }

  protected def nestedDeclarations(model: BaseUnit): Unit = {
    model.references.foreach {
      case f: Fragment =>
        ctx.declarations += (f.location, f)
        nestedDeclarations(f)
      case m: Module  if m.annotations.find(classOf[Aliases]).isDefined =>
        val nestedCtx = new Raml10WebApiContext(ParserContext())
        m.declares.foreach { declaration => processDeclaration(declaration, nestedCtx, m) }
        m.annotations.find(classOf[Aliases]).getOrElse(Aliases(Set())).aliases.map(_._1).foreach { alias =>
          ctx.declarations.libraries += (alias -> nestedCtx.declarations)
        }
        nestedDeclarations(m)
    }
  }

  protected def processDeclaration(declaration: DomainElement, nestedCtx: RamlWebApiContext, model: BaseUnit): Unit = {
    declaration.annotations.find(classOf[ResolvedNamedEntity]) match {
      case Some(resolvedNamedEntity) =>
        resolvedNamedEntity.vals.foreach { case (prefix, namedEntities) =>
          val inContext = namedEntities.find(entity => entity.isInstanceOf[DomainElement] && entity.asInstanceOf[DomainElement].id.contains(model.location))
          declaration match {
            // we recover the local alias we removed when resolving
            case element: NamedDomainElement if inContext.isDefined =>
              val localName = inContext.get.name
              val realName = element.name
              element.withName(localName)
              nestedCtx.declarations += declaration
              element.withName(realName)
            case _ =>
              nestedCtx.declarations += declaration
          }
        }
      case _ => nestedCtx.declarations += declaration
    }
  }

  def asEndPoint(r: ParametrizedResourceType, context: Context): EndPoint = {
    Option(r.target) match {
      case Some(rt: ResourceType) =>
        val node = rt.dataNode.cloneNode()
        node.replaceVariables(context.variables)

        val document = YDocument {
          _.obj {
            _.entry(
              "/endpoint",
              DataNodeEmitter(node, SpecOrdering.Default).emit(_)
            )
          }
        }
        val endPointEntry = document.as[YMap].entries.head
        val collector     = ListBuffer[EndPoint]()

        declarations(context.model)
        ctx.factory.endPointParser(endPointEntry, _ => EndPoint(), None, collector, true).parse()

        collector.toList match {
          case e :: Nil => e
          case Nil      => throw new Exception(s"Couldn't parse an endpoint from resourceType '${r.name}'.")
          case _        => throw new Exception(s"Nested endpoints found in resourceType '${r.name}'.")
        }

      case _ => throw new Exception(s"Cannot find target for parametrized resource type ${r.id}")
    }
  }

  private def transform(model: BaseUnit)(element: DomainElement, isCycle: Boolean): Option[DomainElement] =
    element match {
      case e: EndPoint => Some(convert(model, e))
      case other       => Some(other)
    }

  private def collectResourceTypes(endpoint: EndPoint, context: Context): ListBuffer[EndPoint] = {
    val result = ListBuffer[EndPoint]()
    collectResourceTypes(result, endpoint, context)
    result
  }

  private def collectResourceTypes(collector: ListBuffer[EndPoint], endpoint: EndPoint, initial: Context): Unit = {
    endpoint.resourceType.foreach { resourceType =>
      val context = initial.add(resourceType.variables)

      val resolved = asEndPoint(resourceType, context)
      collector += resolved

      collectResourceTypes(collector, resolved, context)
    }
  }

  /** Apply specified ResourceTypes to given EndPoint. */
  def apply(endpoint: EndPoint, resourceTypes: ListBuffer[EndPoint]): EndPoint = {
    resourceTypes.foldLeft(endpoint) {
      case (current, resourceType) => merge(current, resourceType)
    }
  }

  private def convert(model: BaseUnit, endpoint: EndPoint): EndPoint = {

    val context = Context(model)
      .add("resourcePath", resourcePath(endpoint))
      .add("resourcePathName", resourcePathName(endpoint))

    val resourceTypes = collectResourceTypes(endpoint, context)
    apply(endpoint, resourceTypes) // Apply ResourceTypes to EndPoint

    val resolver = TraitResolver()

    // Iterate operations and resolve extends with inherited traits.
    endpoint.operations.foreach { operation =>
      val local = context.add("methodName", operation.method)

      val branches = ListBuffer[BranchContainer]()

      // Method branch
      branches += Branches.method(resolver, operation, local)

      // EndPoint branch
      branches += Branches.endpoint(resolver, endpoint, local)

      // ResourceType branches
      resourceTypes.foreach { rt =>
        branches += Branches.resourceType(resolver, rt, local, operation.method)
      }

      // Compute final traits
      val traits = branches.foldLeft(Seq[TraitBranch]()) {
        case (current, container) =>
          BranchContainer.merge(current, container.flatten()).collect { case t: TraitBranch => t }
      }

      // Merge traits into operation
      traits.foldLeft(operation) {
        case (current, branch) => DomainElementMerging.merge(current, branch.operation)
      }

      if (removeFromModel) operation.fields.remove(DomainElementModel.Extends)
    }

    if (removeFromModel) endpoint.fields.remove(DomainElementModel.Extends)

    endpoint
  }

  private def resourcePathName(endPoint: EndPoint): String = {
    resourcePath(endPoint)
      .split('/')
      .reverse
      .find(s => s.nonEmpty && "\\{.*\\}".r.findFirstIn(s).isEmpty)
      .getOrElse("")
  }

  private def resourcePath(endPoint: EndPoint) = endPoint.path.replaceAll("\\{ext\\}", "")

  private def findExtendsPredicate(element: DomainElement): Boolean = element.isInstanceOf[EndPoint]

  object Branches {
    def apply(branches: Seq[Branch]): BranchContainer = BranchContainer(branches)

    def endpoint(resolver: TraitResolver, endpoint: EndPoint, context: Context): BranchContainer = {
      BranchContainer(resolveTraits(resolver, endpoint.traits, context))
    }

    def resourceType(traits: TraitResolver,
                     resourceType: EndPoint,
                     context: Context,
                     operation: String): BranchContainer = {

      // Resolve resource type method traits
      val o = resourceType.operations
        .find(_.method == operation)
        .map(method(traits, _, context).flatten())
        .getOrElse(Seq())

      // Resolve resource type traits
      val e = endpoint(traits, resourceType, context).flatten()

      BranchContainer(BranchContainer.merge(o, e))
    }

    def method(resolver: TraitResolver, operation: Operation, context: Context): BranchContainer = {
      BranchContainer(resolveTraits(resolver, operation.traits, context))
    }

    private def resolveTraits(resolver: TraitResolver, parameterized: Seq[ParametrizedTrait], context: Context) = {
      parameterized.map(resolver.resolve(_, context))
    }
  }

  case class ResourceTypeResolver(model: BaseUnit) {
    val resolved: mutable.Map[Key, TraitBranch] = mutable.Map()
  }

  case class TraitResolver() {

    val resolved: mutable.Map[Key, TraitBranch] = mutable.Map()

    def resolve(t: ParametrizedTrait, context: Context): TraitBranch = {
      val local = context.add(t.variables)
      val key   = Key(t.target.id, local)
      resolved.getOrElseUpdate(key, resolveOperation(key, t, context))
    }

    private def resolveOperation(key: Key, parameterized: ParametrizedTrait, context: Context): TraitBranch = {
      val local = context.add(parameterized.variables)

      Option(parameterized.target) match {
        case Some(t: Trait) =>
          val node: DataNode = t.dataNode.cloneNode()
          node.replaceVariables(local.variables)

          val op = dataNodeToOperation(node, context)

          val children = op.traits.map(resolve(_, context))

          TraitBranch(key, op, children)
        case m => throw new Exception(s"Looking for trait but $m was found on model ${context.model}")
      }
    }

    private def dataNodeToOperation(node: DataNode, context: Context): Operation = {
      val document = YDocument {
        _.obj {
          _.entry(
            "extends",
            DataNodeEmitter(node, SpecOrdering.Default).emit(_)
          )
        }
      }

      val entry = document.as[YMap].entries.head
      declarations(context.model)
      ctx.factory.operationParser(entry, _ => Operation(), true).parse()
    }
  }

  case class TraitBranch(key: Key, operation: Operation, children: Seq[Branch]) extends Branch

}
