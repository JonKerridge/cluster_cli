package cluster_cli.run

import cluster_cli.processes.Manager
import cluster_cli.records.Acknowledgement
import cluster_cli.records.ClassDefinitions
import cluster_cli.records.ExtractVersion
import cluster_cli.records.ParseRecord
import cluster_cli.records.TerminalIndex
import cluster_cli.records.VersionControl
import groovy_jcsp.ChannelOutputList
import groovy_jcsp.PAR
import jcsp.lang.CSProcess
import jcsp.lang.ChannelInput
import jcsp.net2.NetChannel
import jcsp.net2.NetChannelOutput
import jcsp.net2.Node
import jcsp.net2.mobile.CodeLoadingChannelFilter
import jcsp.net2.tcpip.TCPIPNodeAddress

/**
 * A class used at the host node to load an application over a local loop-back or
 * a real workstation network.
 * The host node must be executed BEFORE any of the other nodes.
 * The class has a single method invoke()
 */
class HostRun {

  String structureFileName, nature  // nature is either 'Net' default or 'Local'
  String version = VersionControl.versionTag
  Class emitClass
  Class collectClass
  String fileBasename
  int portNumber = 56789  //port number used in TCPIP communications

  /**
   * A class used to run the host node of a cluster
   * This version assumes the cluster will operate over a real network of workstations
   *
   * @param structureFile the full path name of the file holding the parsed structure,
   * excluding the suffix
   * @param emitName the class name of the data object to be processed
   * @param collectName the class name of the object used to collect the resulting output
   */
  HostRun(String structureFile,
          String emitName,
          String collectName){
    this.fileBasename = structureFile
    this.structureFileName = structureFile + ".clicstruct"
    emitClass = Class.forName(emitName)
    collectClass = Class.forName(collectName)
    nature = "Net"
  }
/**
 * A class used to run the host node of a cluster
 * This version invokes a cluster that can operate over a real network of workstations
 * or locally using loop back network addresses
 *
 * @param structureFile the full path name of the file holding the parsed structure,
 * excluding the suffix
 * @param emitName the class name of the data object to be processed
 * @param collectName the class name of the object used to collect the resulting output
 * @param nature Net (default) or Local; Local implies use of a local loopback network using 127.0.0.0 addresses
 */
  HostRun(String structureFile,
          String emitName,
          String collectName,
          String nature){
    this.fileBasename = structureFile
    this.structureFileName = structureFile + ".clicstruct"
    emitClass = Class.forName(emitName)
    collectClass = Class.forName(collectName)
    this.nature = nature
  }

/**
 * A class used to run the host node of a cluster
 * This version assumes the cluster will operate over a real network of workstations
 *
 * @param structureFile the full path name of the file holding the parsed structure,
 * excluding the suffix
 * @param emitClass the name of the class containing the object to be processed
 * @param collectClass the name of the class used to collect the resulting output
 */
  HostRun(String structureFile,
          Class emitClass,
          Class collectClass){
    this.fileBasename = structureFile
    this.structureFileName = structureFile + ".clicstruct"
    this.emitClass = emitClass
    this.collectClass = collectClass
    nature = "Net"
  }
/**
 * A class used to run the host node of a cluster
 * This version invokes a cluster that can operate over a real network of workstations
 * or locally using loop back network addresses
 *
 * @param structureFile the full path name of the file holding the parsed structure,
 * excluding the suffix
 * @param emitClass the name of the class containing the object to be processed
 * @param collectClass the name of the class used to collect the resulting output
 * @param nature Net (default) or Local; Local implies use of a local loopback network using 127.0.0.0 addresses
 */
  HostRun(String structureFile,
          Class emitClass,
          Class collectClass,
          String nature){
    this.fileBasename = structureFile
    this.structureFileName = structureFile + ".clicstruct"
    this.emitClass = emitClass
    this.collectClass = collectClass
    this.nature = nature
  }
/**
 * invoke is the only method in the class HostRun() and is used
 * to create the application process architecture as specified in the
 * structureFile.  The parameterless version produces minimal output during
 * the application loading phase
 */
  void invoke() {
    invoke(false)
  }
  /**
   *
   * @param verbose if true prints comprehensive details of the application loading process
   */
  void invoke(boolean verbose) {
    if (!ExtractVersion.extractVersion(version, nature)){
      println "cluster_cli:Version $version needs to be downloaded, please modify the gradle.build file"
      System.exit(-1)
    }

    File objFile = new File(structureFileName)
    List <ParseRecord> structure = []
    objFile.withObjectInputStream { inStream ->
      inStream.eachObject { structure << (it as ParseRecord) } }
    int requiredManagers = structure.size() - 2
    int totalNodes, totalWorkers
    totalNodes = 0
    totalWorkers = 0
    structure.each {
      totalNodes = totalNodes + it.nodes
      totalWorkers = totalWorkers + (it.nodes * it.workers)
    }

//    String hostIP = structure[0].hostAddress
    String parsedVersion = structure[0].version
    assert parsedVersion == version:"Host: parser ($parsedVersion) and run ($version) version tags do not match"
    if (verbose) println "Host starting version: $version "
    // no longer need structure[0]
    structure.remove(0)

    long startTime, endTime, processingStart, totalElapsed, processingElapsed
    startTime = System.currentTimeMillis()

//Phase 1 get IP addresses of the other nodes

//create the host node
    TCPIPNodeAddress hostNodeAddress
    if (nature == "Net")
      hostNodeAddress = new TCPIPNodeAddress( portNumber)  // find most global IP address available
    else
      hostNodeAddress = new TCPIPNodeAddress( "127.0.0.1", portNumber)  // assumed local host IP
    Node.getInstance().init(hostNodeAddress)
    String nodeIP = hostNodeAddress.getIpAddress()
//    assert hostIP == nodeIP: "Expected hostIP: host does not match actual nodeIP $nodeIP"
// get all nodes' IP addresses
    def fromNodes = NetChannel.numberedNet2One(1)
    println "Host starting needing $totalNodes nodes; with $nodeIP using port $portNumber as host node; using software version $version"
    if (verbose) println "creating $totalWorkers internal processes and $requiredManagers manager processes"
    List<String> nodeIPstrings = []
// assumes nodes have created the corresponding net input channels
    for (n in 1..totalNodes) nodeIPstrings << (fromNodes.read() as String)
    if (verbose) println "Node IPs are $nodeIPstrings"
    processingStart = System.currentTimeMillis()
// create the hostToNodes channels
    ChannelOutputList hostToNodes     // writes to node[i] fromHost using vcn = 1
    hostToNodes = []
    nodeIPstrings.each { nodeIPString ->
      def nodeAddress = new TCPIPNodeAddress(nodeIPString, portNumber)
      def toNode = NetChannel.one2net(nodeAddress, 1, new CodeLoadingChannelFilter.FilterTX())
      hostToNodes.append(toNode)
    }
//    println "Host to NodeLoader Channels created"
//send classes used to emit and collect objects to each nodeLoader
    ClassDefinitions classDefinitions = new ClassDefinitions(emitClass: emitClass,
        collectClass: collectClass, version: version)
    for (n in 0..<totalNodes) hostToNodes[n].write(classDefinitions)
    if (verbose) println "Host has sent class definitions to each node"

// find from structure those nodes that are allocated to fixed IPs
    List preAllocatedIPs = []
    structure.each { record -> if (record.fixedIPAddresses != []) preAllocatedIPs = preAllocatedIPs + record.fixedIPAddresses
    }
    if (verbose) println "PreAllocated nodes is $preAllocatedIPs"
// now check that all the preAllocatedIPs have actually been started
    boolean preAllocated = true
    preAllocatedIPs.each { ip ->
      if (!nodeIPstrings.contains(ip)) {
        println "Preallocated node $ip not in node IPs $preAllocatedIPs"
        preAllocated = false
      } else nodeIPstrings.remove(ip)
    }
// if not all pre-allocated nodes have been started exit ungracefully
    if (!preAllocated) {
      println "Host terminating early - all the required pre-allocated nodes have not been started"
      println "The nodeLoader processes will be terminated automatically with status -2"
      for (n in 0..<totalNodes) hostToNodes[n].write(new TerminalIndex())
      System.exit(-2)
    }
// know that all the preAllocated nodes can be used as is
// and that remaining nodes can be used in any way
// or that no nodes were preAllocated
    structure.each { record ->
      record.allocatedNodeIPs = []
      if (record.fixedIPAddresses == []) {
        // allocate from remaining nodes in nodeIPstrings
        for (n in 1..record.nodes) {
          if (verbose) println "Host popping from $nodeIPstrings"
          record.allocatedNodeIPs << nodeIPstrings.pop()
        }
      } else {
        // transfer fixed IPs to allocated
        for (n in 1..record.nodes) record.allocatedNodeIPs << record.fixedIPAddresses.pop()
      }
    } // pre-allocated nodes have been assigned
// now print the updated structure , testing only
    if (verbose) {
      int sno = 0
      structure.each {
        println "$sno $it"
        sno = sno + 1
      }
    }
// now send structure to each nodeLoader
    for (n in 0..<totalNodes) hostToNodes[n].write(structure)
// now wait for Nodes to respond
    List<Acknowledgement> acks
    acks = []
    for (n in 1..totalNodes) acks << (fromNodes.read() as Acknowledgement)
    acks.each { ack -> assert ack.ackValue == 1:
        "Expecting ack value 1 got ${ack.ackValue} from ${ack.ackString}" }
    if (verbose) println "Host has completed phase 1"

// can now start phase 2 - all net input channels are created in host and nodeLoaders
    for (n in 0..<totalNodes) hostToNodes[n].write(new Acknowledgement(2, "host"))
// create net input channels for the manager processes
    List <List <ChannelInput>> sendReadyInputChannels // the inner list has two entries [ send, read]
    sendReadyInputChannels = []
    for (rm in 0 ..< requiredManagers){
      int sendIndex = (rm*2) + 100
      int readIndex = (rm*2) + 101
//      println "channel indices $rm send $sendIndex, read $readIndex"
      def readyToSend = NetChannel.numberedNet2One(sendIndex)
      def readyToRead = NetChannel.numberedNet2One(readIndex)
      sendReadyInputChannels << ([readyToSend, readyToRead] as List<ChannelInput>)
      if (verbose) println "sendReadyInputChannels manager $rm" +
                            "\n\t readyToSend: ${readyToSend.getLocation()}" +
                            "\n\t readyToRead: ${readyToRead.getLocation()}"
    }

// input channels now created for all managers , await nodes completing their input channels
    acks = []
    for (n in 1..totalNodes) acks << (fromNodes.read() as Acknowledgement)
    acks.each { ack -> assert ack.ackValue == 2:
        "Expecting ack value 2 got ${ack.ackValue} from ${ack.ackString}" }
    if (verbose) println "Host has completed phase 2"
// now start phase 3, creation of the corresponding net output channels
    for (n in 0..<totalNodes) hostToNodes[n].write(new Acknowledgement(3, "host"))
// create ChannelOutputLists for useIndex and terminate for each manager
    List <ChannelOutputList > indexList = []
    for ( rm in 0 ..< requiredManagers){
      ChannelOutputList subList = new ChannelOutputList()
      // index relates to cluster rm; terminate relates to cluster rm+1
      for ( i in 0 ..< structure[rm].allocatedNodeIPs.size()){
        def nextNodeAddress = new TCPIPNodeAddress(structure[rm].allocatedNodeIPs[i], portNumber)
        subList.append(NetChannel.one2net(nextNodeAddress, 20))
      }
      if (verbose) {
        println "Manager $rm ChannelOutputList"
        subList.each { entry ->
          for (i in 0..<entry.size()) println "index: $i: ${(entry[i] as NetChannelOutput).getLocation()}"
        }
      }
      indexList << subList
    } // requiredManagers
    acks = []
    for (n in 1..totalNodes) acks << (fromNodes.read() as Acknowledgement)
    acks.each { ack -> assert ack.ackValue == 3:
        "Expecting ack value 3 got ${ack.ackValue} from ${ack.ackString}" }
    if (verbose) println "Host has completed phase 3"

// now start phase 4, creation of the manager process network
    for (n in 0..<totalNodes) hostToNodes[n].write(new Acknowledgement(4, "host"))

    if (verbose) println "Host host now invoking $requiredManagers manager process(es)"
    List<CSProcess> hostManagers = []
    for ( i in 0 ..< requiredManagers){
      if (verbose) println "Host creating manager $i"
      Manager manager = new Manager(
          readyToSend: sendReadyInputChannels[i][0],
          readyToRead: sendReadyInputChannels[i][1],
          sendTo: indexList[i],
          nWriteNodes: structure[i].nodes,
          nReadNodes: structure[i+1].nodes,
          nReadInternals: structure[i+1].workers ,
          clusterIndex: i
      )
      if (verbose) println "Host created manager $i"
      hostManagers << manager
    }
    new PAR(hostManagers).run()
    if (verbose) println "Host Managers have terminated"
// host is terminating
    endTime = System.currentTimeMillis()
    totalElapsed = endTime - startTime
    processingElapsed = endTime - processingStart
    List <TerminalIndex> finalTimings
    finalTimings = []
    for (n in 1..totalNodes) finalTimings << (fromNodes.read() as TerminalIndex)
// now sort the returned timings into cluster, node order
    finalTimings = finalTimings.sort{ a, b ->
      if (a.clusterIndex == b.clusterIndex)
        a.nodeIndex <=> b.nodeIndex
      else
        a.clusterIndex <=> b.clusterIndex
    }
    println "Host elapsed, $totalElapsed, processing, $processingElapsed"
    finalTimings.each{println "$it"}
    // now copy times to an output file in csv format
    String[] tokens = fileBasename.split("\\W")
    int nTokens = tokens.size() - 1
//    println "Host $tokens"
    String timeFileName = "./${tokens[nTokens]}-times.csv"
    PrintWriter outputTimes = null
    File timeFile  = new File(timeFileName)
    if (timeFile.exists()){
      outputTimes = new PrintWriter(new FileOutputStream(new File(timeFileName), true))
    }
    else {
      outputTimes = new PrintWriter(timeFileName)
    }
    finalTimings.each {ti ->
      outputTimes.println "${tokens[nTokens]}, ${ti.nodeIP}, ${ti.clusterIndex}, ${ti.nodeIndex}, ${ti.elapsed}, ${ti.processing}, "
    }
    // added in 1.0.6
    outputTimes.println "Host,,,,,,${totalElapsed},${processingElapsed}"
    outputTimes.flush()
    outputTimes.close()
    println "Host has terminated"

  } // invoke

  static void main(String[] args) {
    switch (args.size()){
      case 3:
        new HostRun(args[0], args[1], args[2]).invoke()
        break
      case 4:
        new HostRun(args[0], args[1], args[2], args[3]).invoke()
        break
      default:
        println "HostRun: wrong number of arguments ${args} supplied" +
            "\n\t all arguments are Strings" +
            "\n\targs[0] - full path name of the parsed structure file without suffix" +
            "\n\targs[1] - name of class used to emit objects into the network" +
            "\n\targs[2] - name of class used to process and collect resultant objects" +
            "\n\targs[3] - optional - if specified has value 'Local'"
    }
  }

}
