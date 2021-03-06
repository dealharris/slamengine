package slamdata.engine.physical.mongodb

import scalaz._
import Scalaz._

import slamdata.engine.fp._
import slamdata.engine.{RenderTree, Terminal, NonTerminal}

/**
 * A workflow consists of one or more tasks together with the collection
 * where the results of executing the workflow will be placed.
 */
sealed case class Workflow(task: WorkflowTask)

object Workflow {
  implicit def WorkflowRenderTree(implicit RT: RenderTree[WorkflowTask]) =
    new RenderTree[Workflow] {
      def render(wf: Workflow) =
        NonTerminal("", List(RT.render(wf.task)), List("Workflow"))
    }
}

sealed trait WorkflowTask

object WorkflowTask {
  implicit def WorkflowTaskRenderTree(implicit RP: RenderTree[Pipeline], RJ: RenderTree[Js], RS: RenderTree[Selector]) =
    new RenderTree[WorkflowTask] {
      val WorkflowTaskNodeType = List("Workflow", "WorkflowTask")
  
      def render(task: WorkflowTask) = task match {
        case ReadTask(value) => Terminal(value.name, WorkflowTaskNodeType :+ "ReadTask")
        
        case PipelineTask(source, pipeline) =>
          NonTerminal(
            "",
            render(source) :: 
              RP.render(pipeline) ::
              Nil,
            WorkflowTaskNodeType :+ "PipelineTask")
            
        case FoldLeftTask(head, tail) =>
          NonTerminal(
            "",
            render(head) ::
              tail.map(render(_)).toList,
            WorkflowTaskNodeType :+ "FoldLeftTask")

        case MapReduceTask(source, MapReduce(map, reduce, outOpt, selectorOpt, sortOpt, limitOpt, finalizerOpt, scopeOpt, jsModeOpt, verboseOpt)) =>
          NonTerminal("",
            render(source) ::
              RJ.render(map) ::
              RJ.render(reduce) ::
              Terminal(outOpt.toString) ::
              selectorOpt.map(RS.render(_)).getOrElse(Terminal("None")) ::
              sortOpt.map(keys => NonTerminal("", (keys.map { case (expr, ot) => Terminal(expr.toString + " -> " + ot, WorkflowTaskNodeType :+ "MapReduceTask" :+ "Sort" :+ "Key") } ).toList,
                WorkflowTaskNodeType :+ "MapReduceTask" :+ "Sort")).getOrElse(Terminal("None")) ::
              Terminal(limitOpt.toString) ::
              finalizerOpt.map(RJ.render(_)).getOrElse(Terminal("None")) ::
              Terminal(scopeOpt.toString) ::
              Terminal(jsModeOpt.toString) ::
              Nil,
            WorkflowTaskNodeType :+ "MapReduceTask")

        case _ => Terminal(task.toString, WorkflowTaskNodeType)
      }
    }

  /**
   * A task that returns a necessarily small amount of raw data.
   */
  case class PureTask(value: Bson) extends WorkflowTask

  /**
   * A task that merely sources data from some specified collection.
   */
  case class ReadTask(value: Collection) extends WorkflowTask

  /**
   * A task that executes a Mongo read query.
   */
  case class QueryTask(
    source: WorkflowTask,
    query: FindQuery,
    skip: Option[Int],
    limit: Option[Int])
      extends WorkflowTask

  /**
   * A task that executes a Mongo pipeline aggregation.
   */
  case class PipelineTask(source: WorkflowTask, pipeline: Pipeline)
      extends WorkflowTask

  /**
   * A task that executes a Mongo map/reduce job.
   */
  case class MapReduceTask(source: WorkflowTask, mapReduce: MapReduce)
      extends WorkflowTask

  /**
   * A task that executes a sequence of other tasks, one at a time, collecting
   * the results in the same collection. The first task must produce a new 
   * collection, and the remaining tasks must be able to merge their results
   * into an existing collection, hence the types.
   */
  case class FoldLeftTask(head: WorkflowTask, tail: NonEmptyList[MapReduceTask])
      extends WorkflowTask

  /**
   * A task that executes a number of others in parallel and merges them
   * into the same collection.
   */
  case class JoinTask(steps: Set[WorkflowTask]) extends WorkflowTask

  /**
   * A task that evaluates some code on the server. The JavaScript function
   * must accept two parameters: the source collection, and the destination 
   * collection.
   */
  // case class EvalTask(source: WorkflowTask, code: Js.FuncDecl)
  //     extends WorkflowTask
}
