package cluster_cli

import cluster_cli.run.NodeRun

class Node6 {
  static void main(String[] args) {
    new NodeRun("127.0.0.1", "127.0.0.6").invoke()
  }
}
