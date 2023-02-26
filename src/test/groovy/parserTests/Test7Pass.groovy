package parserTests

import cluster_cli.parse.Parser
import groovy.test.GroovyTestCase
import org.junit.jupiter.api.Test

class Test7Pass extends GroovyTestCase {
  @Test
  void test(){
    String inFileName = "D:/IJGradle/cluster_cli/src/test/groovy/parserTests/test7"
    Parser parser = new Parser(inFileName)
    assertTrue (parser.parse())
  }
}
