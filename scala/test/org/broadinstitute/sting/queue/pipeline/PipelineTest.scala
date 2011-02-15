/*
 * Copyright (c) 2011, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.queue.pipeline

import collection.JavaConversions._
import org.broadinstitute.sting.utils.Utils
import org.testng.Assert
import org.broadinstitute.sting.commandline.CommandLineProgram
import java.util.Date
import java.text.SimpleDateFormat
import org.broadinstitute.sting.{WalkerTest, BaseTest}
import org.broadinstitute.sting.queue.{QException, QCommandLine}
import org.broadinstitute.sting.datasources.pipeline.{Pipeline, PipelineProject, PipelineSample}
import org.broadinstitute.sting.queue.util.{Logging, ProcessController}
import java.io.{FileNotFoundException, File}

object PipelineTest extends BaseTest with Logging {

  case class K1gBam(squidId: String, sampleId: String, version: Int)

  /** 1000G BAMs used for validation */
  val k1gBams = List(
    new K1gBam("C474", "NA19651", 2),
    new K1gBam("C474", "NA19655", 2),
    new K1gBam("C474", "NA19669", 2),
    new K1gBam("C454", "NA19834", 2),
    new K1gBam("C460", "HG01440", 2),
    new K1gBam("C456", "NA12342", 2),
    new K1gBam("C456", "NA12748", 2),
    new K1gBam("C474", "NA19649", 2),
    new K1gBam("C474", "NA19652", 2),
    new K1gBam("C474", "NA19654", 2))

  validateK1gBams()

  /** The path to the current Sting directory.  Useful when specifying Sting resources. */
  val currentStingDir = new File(".").getAbsolutePath

  /** The path to the current build of the GATK jar in the currentStingDir. */
  val currentGATK = new File(currentStingDir, "dist/GenomeAnalysisTK.jar")

  private val validationReportsDataLocation = "/humgen/gsa-hpprojects/GATK/validationreports/submitted/"

  val run = System.getProperty("pipeline.run") == "run"

  /**
   * Returns the top level output path to this test.
   * @param testName The name of the test passed to PipelineTest.executeTest()
   * @return the top level output path to this test.
   */
  def testDir(testName: String) = "pipelinetests/%s/".format(testName)

  /**
   * Returns the directory where relative output files will be written for this test.
   * @param testName The name of the test passed to PipelineTest.executeTest()
   * @return the directory where relative output files will be written for this test.
   */
  def runDir(testName: String) = testDir(testName) + "run/"

  /**
   * Returns the directory where temp files will be written for this test.
   * @param testName The name of the test passed to PipelineTest.executeTest()
   * @return the directory where temp files will be written for this test.
   */
  def tempDir(testName: String) = testDir(testName) + "temp/"

  /**
   * Encapsulates a file MD5
   * @param testName The name of the test also passed to PipelineTest.executeTest().
   * @param filePath The file path of the output file, relative to the directory the pipeline is run in.
   * @param md5 The expected MD5
   * @return a file md5 that can be appended to the PipelineTestSpec.fileMD5s
   */
  def fileMD5(testName: String, filePath: String, md5: String) = (new File(runDir(testName) + filePath), md5)

  /**
   * Creates a new pipeline from a project.
   * @param project Pipeline project info.
   * @param samples List of samples.
   * @return a new pipeline project.
   */
  def createPipeline(project: PipelineProject, samples: List[PipelineSample]) = {
    val pipeline = new Pipeline
    pipeline.setProject(project)
    pipeline.setSamples(samples)
    pipeline
  }

  /**
   * Creates a new pipeline project for hg19 with b37 132 dbsnp for genotyping, and b37 129 dbsnp for eval.
   * @param projectName Name of the project.
   * @param chr20 True if only chr20 should be evaluated or the whole exome.
   * @return a new pipeline project.
   */
  def createHg19Project(projectName: String, chr20: Boolean) = {
    val project = new PipelineProject
    project.setName(projectName)
    project.setReferenceFile(new File(BaseTest.hg19Reference))
    project.setGenotypeDbsnp(new File(BaseTest.b37dbSNP132))
    project.setEvalDbsnp(new File(BaseTest.b37dbSNP129))
    project.setRefseqTable(new File(BaseTest.hg19Refseq))
    project.setIntervalList(new File(if (chr20) BaseTest.hg19Chr20Intervals else BaseTest.hg19Intervals))
    project
  }

  /**
   * Creates a 1000G pipeline sample from one of the bams.
   * @param idPrefix Text to prepend to the sample name.
   * @param k1gBam bam to create the sample for.
   * @return the created pipeline sample.
   */
  def createK1gSample(idPrefix: String, k1gBam: K1gBam) = {
    val sample = new PipelineSample
    sample.setId(idPrefix + "_" + k1gBam.sampleId)
    sample.setBamFiles(Map("cleaned" -> getPicardBam(k1gBam)))
    sample.setTags(Map("SQUIDProject" -> k1gBam.squidId, "CollaboratorID" -> k1gBam.sampleId))
    sample
  }

  /**
   * Runs the pipelineTest.
   * @param pipelineTest test to run.
   */
  def executeTest(pipelineTest: PipelineTestSpec) {
    val name = pipelineTest.name
    if (name == null)
      throw new QException("PipelineTestSpec.name is null.")
    println(Utils.dupString('-', 80));
    executeTest(name, pipelineTest.args, pipelineTest.jobQueue, pipelineTest.expectedException)
    if (run) {
      assertMatchingMD5s(name, pipelineTest.fileMD5s.map{case (file, md5) => new File(runDir(name), file) -> md5})
      if (pipelineTest.evalSpec != null)
        validateEval(name, pipelineTest.evalSpec)
      println("  => %s PASSED".format(name))
    }
    else
      println("  => %s PASSED DRY RUN".format(name))
  }

  private def assertMatchingMD5s(name: String, fileMD5s: Traversable[(File, String)]) {
    var failed = 0
    for ((file, expectedMD5) <- fileMD5s) {
      val calculatedMD5 = BaseTest.testFileMD5(name, file, expectedMD5, false)
      if (expectedMD5 != "" && expectedMD5 != calculatedMD5)
        failed += 1
    }
    if (failed > 0)
      Assert.fail("%d of %d MD5s did not match.".format(failed, fileMD5s.size))
  }

  private def validateEval(name: String, evalSpec: PipelineTestEvalSpec) {
    // write the report to the shared validation data location
    val formatter = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss")
    val reportLocation = "%s%s/validation.%s.eval".format(validationReportsDataLocation, name, formatter.format(new Date))
    new File(reportLocation).getParentFile.mkdirs

    // Run variant eval generating the report and validating the pipeline vcf.
    var walkerCommand = "-T VariantEval -R %s -B:eval,VCF %s -E %s -reportType R -reportLocation %s -L %s"
      .format(evalSpec.reference, evalSpec.vcf, evalSpec.evalModules.mkString(" -E "), reportLocation, evalSpec.intervals)

    if (evalSpec.dbsnp != null) {
      val dbsnpArg = if (evalSpec.dbsnp.getName.toLowerCase.endsWith(".vcf")) "-B:dbsnp,VCF" else "-D"
      walkerCommand += " %s %s".format(dbsnpArg, evalSpec.dbsnp)
    }

    if (evalSpec.intervals != null)
      walkerCommand += " -L %s".format(evalSpec.intervals)

    for (validation <- evalSpec.validations) {
      walkerCommand += " -summary %s".format(validation.metric)
      walkerCommand += " -validate '%1$s >= %2$s' -validate '%1$s <= %3$s'".format(
        validation.metric, validation.min, validation.max)
    }

    WalkerTest.executeTest(name + "-validate", walkerCommand, null)
  }

  /**
   * execute the test
   * @param name the name of the test
   * @param args the argument list
   * @param jobQueue the queue to run the job on.  Defaults to hour if jobQueue is null.
   * @param expectedException the expected exception or null if no exception is expected.
   */
  private def executeTest(name: String, args: String, jobQueue: String, expectedException: Class[_]) {
    var command = Utils.escapeExpressions(args)

    // add the logging level to each of the integration test commands

    command = Utils.appendArray(command, "-bsub", "-l", "WARN", "-tempDir", tempDir(name), "-runDir", runDir(name))

    if (jobQueue == null)
      command = Utils.appendArray(command, "-jobQueue", "hour")
    else
      command = Utils.appendArray(command, "-jobQueue", jobQueue)

    if (run)
      command = Utils.appendArray(command, "-run")

    // run the executable
    var gotAnException = false

    val instance = new QCommandLine
    runningCommandLines += instance
    try {
      println("Executing test %s with Queue arguments: %s".format(name, Utils.join(" ",command)))
      CommandLineProgram.start(instance, command)
    } catch {
      case e =>
        gotAnException = true
        if (expectedException != null) {
          // we expect an exception
          println("Wanted exception %s, saw %s".format(expectedException, e.getClass))
          if (expectedException.isInstance(e)) {
            // it's the type we expected
            println(String.format("  => %s PASSED", name))
          } else {
            e.printStackTrace()
            Assert.fail("Test %s expected exception %s but got %s instead".format(
              name, expectedException, e.getClass))
          }
        } else {
          // we didn't expect an exception but we got one :-(
          throw new RuntimeException(e)
        }
    } finally {
      instance.shutdown()
      runningCommandLines -= instance
    }

    // catch failures from the integration test
    if (expectedException != null) {
      if (!gotAnException)
      // we expected an exception but didn't see it
        Assert.fail("Test %s expected exception %s but none was thrown".format(name, expectedException.toString))
    } else {
      if (CommandLineProgram.result != 0)
        throw new RuntimeException("Error running the GATK with arguments: " + args)
    }
  }

  /**
   * Throws an exception if any of the 1000G bams do not exist and warns if they are out of date.
   */
  private def validateK1gBams() {
    var missingBams = List.empty[File]
    for (k1gBam <- k1gBams) {
      val latest = getLatestVersion(k1gBam)
      val bam = getPicardBam(k1gBam)
      if (k1gBam.version != latest)
        logger.warn("1000G bam is not the latest version %d: %s".format(latest, k1gBam))
      if (!bam.exists)
        missingBams :+= bam
    }
    if (missingBams.size > 0) {
      val nl = "%n".format()
      throw new FileNotFoundException("The following 1000G bam files are missing.%n%s".format(missingBams.mkString(nl)))
    }
  }

  private def getPicardBam(k1gBam: K1gBam): File =
    getPicardBam(k1gBam.squidId, k1gBam.sampleId, k1gBam.version)

  private def getPicardBam(squidId: String, sampleId: String, version: Int): File =
    new File(getPicardDir(squidId, sampleId, version), sampleId + ".bam")

  private def getPicardDir(squidId: String, sampleId: String, version: Int) =
    new File("/seq/picard_aggregation/%1$s/%2$s/v%3$s/".format(squidId, sampleId, version))

  private def getLatestVersion(k1gBam: K1gBam): Int =
    getLatestVersion(k1gBam.squidId, k1gBam.sampleId, k1gBam.version)

  private def getLatestVersion(squidId: String, sampleId: String, startVersion: Int): Int = {
    var version = startVersion
    while (new File(getPicardDir(squidId, sampleId, version + 1), "finished.txt").exists)
      version += 1
    version
  }

  private var runningCommandLines = Set.empty[QCommandLine]

  Runtime.getRuntime.addShutdownHook(new Thread {
    /** Cleanup as the JVM shuts down. */
    override def run {
      try {
        ProcessController.shutdown()
      } catch {
        case _ => /*ignore */
      }
      runningCommandLines.foreach(commandLine =>
        try {
          commandLine.shutdown()
        } catch {
          case _ => /* ignore */
        })
    }
  })
}
