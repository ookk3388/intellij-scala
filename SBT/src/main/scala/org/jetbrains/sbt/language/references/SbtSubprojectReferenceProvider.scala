package org.jetbrains.sbt
package language.references

import java.io.File

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi._
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScClassParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * @author Nikolay Obedin
 * @since 8/26/14.
 */
class SbtSubprojectReferenceProvider extends PsiReferenceProvider {

  import org.jetbrains.sbt.language.references.SbtSubprojectReferenceProvider._

  def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    if (element.getContainingFile.getFileType.getName != Sbt.Name) return Array.empty
    extractSubprojectPath(element).flatMap { path =>
      findBuildFile(path, element.getProject).map(new SbtSubprojectReference(element, _))
    }.toArray
  }

  private def findBuildFile(subprojectPath: String, project: Project): Option[PsiFile] = {
    FilenameIndex.getFilesByName(project, "build.sbt", GlobalSearchScope.allScope(project)).find { file =>
      val relativeToProjectPath = project.getBasePath + File.separator + subprojectPath
      val absolutePath = FileUtil.toSystemIndependentName(FileUtil.toCanonicalPath(relativeToProjectPath))
      Option(file.getParent).map(_.getVirtualFile.getPath).fold(false)(FileUtil.comparePaths(_, absolutePath) == 0)
    }
  }

  private def extractSubprojectPath(element: PsiElement): Option[String] = {
    for {
      listOfPatterns <- Option(element.getParent)
      patternDef <- Option(listOfPatterns.getParent)
    } yield patternDef match {
      case ScPatternDefinition.expr(e) => e match {
        case expr: ScReferenceExpression if expr.getText == "project" => Some(element.getText)
        case call: ScMethodCall => extractSubprojectPathFromProjectCall(call, element)
        case _ => None
      }
      case _ => None
    }
  }.flatten

  private def extractSubprojectPathFromProjectCall(call: ScMethodCall, element: PsiElement) = {
    var result: Option[String] = None
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitMethodCallExpression(call: ScMethodCall) = call match {
        case ScMethodCall(expr, Seq(_: ScLiteral, pathElt)) if expr.getText == "Project" =>
          result = extractPathFromFileParam(pathElt)
        case ScMethodCall(expr, Seq(pathElt)) if expr.getText.matches("^project.+?in$") =>
          result = extractPathFromFileParam(pathElt)
        case ScMethodCall(expr, _) if expr.getText.startsWith("project") =>
          result = Some(element.getText)
          super.visitMethodCallExpression(call)
        case _ =>
          super.visitMethodCallExpression(call)
      }
    }
    call.accept(visitor)
    result
  }

  // TODO: extract these methods into another class and use them to write path completion

  private def extractPathFromFileParam(element: PsiElement): Option[String] = element match {
    case ScTemplateDefinition.ExtendsBlock(block) if element.isInstanceOf[ScNewTemplateDefinition] =>
      block.getChildren.toSeq.headOption.collect {
        case classParent : ScClassParents => classParent.constructor.flatMap(extractPathFromFileCtor)
      }.flatten
    case expr@ScInfixExpr(_, op, _) if op.getText == "/" =>
      extractPathFromConcatenation(expr)
    case expr : ScReferenceExpression =>
      Option(expr.resolve()).flatMap(extractPathFromReference)
    case _ => None
  }

  private def extractPathFromFileCtor(ctor: ScConstructor): Option[String] = {
    ctor.args.map(_.exprs).flatMap {
      case Seq(pathLit : ScLiteral) =>
        pathLit.stringValue
      case Seq(parentLit : ScLiteral, childLit : ScLiteral) =>
        for {
          parent <- parentLit.stringValue
          child  <- childLit.stringValue
        } yield parent + File.separator + child
      case Seq(parentElt, childLit : ScLiteral) if childLit.isString =>
        for {
          parent <- extractPathFromFileParam(parentElt)
          child  <- childLit.stringValue
        } yield parent + File.separator + child
      case _ => None
    }
  }

  private def extractPathFromConcatenation(concatExpr: ScInfixExpr): Option[String] =
    concatExpr.rOp match {
      case partLit : ScLiteral =>
        for {
          parent <- extractPathFromFileParam(concatExpr.lOp)
          child  <- partLit.stringValue
        } yield parent + File.separator + child
      case partRef : ScReferenceExpression =>
        for {
          parent <- extractPathFromFileParam(concatExpr.lOp)
          child  <- extractPathFromFileParam(partRef)
        } yield parent + File.separator + child
      case _ => None
    }

  private def extractPathFromReference(ref: PsiElement): Option[String] = {
    for {
      listOfPatterns <- Option(ref.getParent)
      patternDef <- Option(listOfPatterns.getParent)
    } yield patternDef match {
      case ScPatternDefinition.expr(e) => extractPathFromFileParam(e)
      case _ => None
    }
  }.flatten
}

private class SbtSubprojectReference[T <: PsiElement](val element: T, val sbtFile: PsiFile)
        extends PsiReferenceBase.Immediate[T](element,
          TextRange.create(element.getStartOffsetInParent, element.getStartOffsetInParent + element.getTextLength),
          sbtFile)

private object SbtSubprojectReferenceProvider {
  implicit class RichScLiteral(literal: ScLiteral) {
    def stringValue: Option[String] = {
      if (literal.isString)
        Some(literal.getValue.asInstanceOf[String])
      else
        None
    }
  }
}