package slamdata.engine.physical.mongodb

import slamdata.engine._
import slamdata.engine.DisjunctionMatchers 

import scalaz._
import Scalaz._

import org.specs2.mutable._

class PipelineSpec extends Specification with DisjunctionMatchers {
  def p(ops: PipelineOp*) = Pipeline(ops.toList)

  val empty = p()

  import PipelineOp._
  import ExprOp._

  "Pipeline.merge" should {
    "return left when right is empty" in {
      val l = p(Skip(10), Limit(10))

      l.merge(empty) must (beRightDisj(l))
    }

    "return right when left is empty" in {
      val r = p(Skip(10), Limit(10))

      empty.merge(r) must (beRightDisj(r))
    }

    "return empty when both empty" in {
      empty.merge(empty) must (beRightDisj(empty))
    }

    "return left when left and right are equal" in {
      val v = p(Skip(10), Limit(10))      

      v.merge(v) must (beRightDisj(v))
    }

    "merge two simple projections" in {
      val p1 = Project(Reshape(Map(
        "foo" -> -\/ (Literal(Bson.Int32(1)))
      )))

      val p2 = Project(Reshape(Map(
        "bar" -> -\/ (Literal(Bson.Int32(2)))
      )))   

      val r = Project(Reshape(Map(
        "foo" -> -\/ (Literal(Bson.Int32(1))),
        "bar" -> -\/ (Literal(Bson.Int32(2)))
      ))) 

      p(p1).merge(p(p2)) must (beRightDisj(p(r)))
    }
  }
}