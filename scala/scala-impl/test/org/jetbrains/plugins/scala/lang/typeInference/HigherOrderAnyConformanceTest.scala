package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class HigherOrderAnyConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL11320(): Unit = checkTextHasNoErrors(
    """
      |def f[F[_], C, G[_], A](f: F[C])(implicit ev: C <:< G[A]): F[C] = f
      |f(List(Option(1)))
    """.stripMargin
  )

  def `test (*, *) -> * kind`(): Unit = checkTextHasNoErrors(
    """
      |def f[F[_], A, G[_, _], C, D](f: F[A])(implicit ev: A <:< G[C, D]): F[A] = f
      |
      |trait MyTrait[A, B]
      |val a: MyTrait[Int, String] = ???
      |f(List(a))
    """.stripMargin
  )
}
