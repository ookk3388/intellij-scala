package org.jetbrains.plugins.scala.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtil
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.project.ModuleExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 16.11.11
 */

object ScalaUtil {

  def runnersPath(): String = {
    PathUtil.getJarPathForClass(classOf[Client]).replace("compiler-shared", "runners")
  }

  def findVirtualFile(psiFile: PsiFile): Option[VirtualFile] = {
    Option(psiFile.getVirtualFile).orElse(Option(psiFile.getViewProvider.getVirtualFile).flatMap {
      case light: LightVirtualFile => Option(light.getOriginalFile)
      case _ => None
    })
  }

  def getScalaVersion(file: PsiFile): Option[String] = {
    findVirtualFile(file) flatMap {
      vFile => getModuleForFile(vFile, file.getProject)
    } flatMap {
      module => module.scalaSdk
    } flatMap {
      sdk => sdk.compilerVersion
    }
  }

  def getModuleForFile(virtualFile: VirtualFile, project: Project): Option[Module] =
    Option(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(virtualFile))
  
  def getModuleForFile(file: PsiFile): Option[Module] = {
    import org.jetbrains.plugins.scala.project._
    
    Option(file.getVirtualFile).flatMap {
      vFile => getModuleForFile(vFile, file.getProject)
    }.orElse(file.getProject.anyScalaModule.map(_.module))
  }
}