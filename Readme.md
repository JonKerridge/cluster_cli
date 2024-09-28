The software is used to build clustered computing networks. 
The network is defined by a DSL (Domain Specific Language) 
that uses a command line 
interface based on picocli.  The user has to supply two classes; 
one that defines the data to be processed and the other that 
defines what happens to the final results of processing.

The library has been compiled using Groovy 3 and Java 11

This version of software has been tested using a 
local 127.0.0.0 type network as well as real networks.  

The LocalNode is used to run a single node of the cluster, 
thus multiple instances of the nodes have to be initialised, 
each with a different IP address to form the nodes of the cluster.  
A local host is also required which can access the application classes.  
The local host must be started BEFORE any of the other nodes.  
The nodes assume the host is placed on node 127.0.0.1. 
See the test codes, where there are separate hosts 
for each test and scripts to run the required nodes.

The jar LocalNode contained in the corresponding release can 
also be used to run separate instances of the node process.

The code RunParser is used to parse a definition file and create 
a version that can be used to build the system . This creates a file
with suffix .clicstruct which is used by the rest of the system.  The parser also
produces a readable test file of the parsed output in a file with suffix .clictxt

The parser will copy the software version number in to the parsed output
and all the codes have the version number stored in them.  A check is
made to ensure that the version number is consistent across all software,
at the time of use.

The following is a DSL definition for test5:
<pre>
version 1.0.1
emit -nodes 2 -workers 2 -p int,int!0,23!100,123!200,223!300,323
work -n 2 -w 4 -m update1 -p int!500
collect -n 2 -w 2 -f Test5Results
</pre>

This defines a network of three clusters (emit, work and collect), each with 2 nodes
and varying numbers of workers running in parallel within the node.

An emit node assumes the data object has a create method as defined in 
records.EmitInterface. Parameters for the constructor are passed to each node and
worker in order.  The first entry in the parameter list specifies the data types.
This is followed by the number of nodes multiplied by the number of workers instances of parameter
values, separated by !.  These parameters will govern the number of data objects 
the emit node(s) will create.  In this case there are 2 nodes each having 2 internal emitter processes, 
thus 4 sets of parameters are required.

The work cluster comprises 2 nodes each of 4 internal worker processes.  The name of the method used to
undertake the operation is specified after the  -(m)ethod option.  The parameter string comprises the type specification
followed by required number of parameter values.

The collect cluster comprises 2 nodes of 2 internal collect processes.  It requires the definition of a Collect 
class that implements the records.CollectInterface.  This defines a collate(T data, List params), 
where T is the type of the emitted objects and  a finalise(List params) method that will be
used to process the incoming data objects.  The output can also be stored in a file the base name of which appears
after the -(f)ile option.  The Filenames will be differentiated by the node and internal 
Collect process used to write the file.

The local host for the above specification is invoked as follows (note the .clicstruct suffix is added automatically):

<pre>
class T5Host {
  static void main(String[] args) {
    String structure = "D:\\IJGradle\\cluster_cli\\src\\test\\groovy\\parserTests_101/test5"
    Class emitClass = EmitObject
    Class collectClass = CollectObject
    new HostRun(structure, emitClass, collectClass, "Local").invoke()
  }
}
</pre>

and the node at 127.0.0.6 is invoked as follows:

<pre>
class Node6 {
  static void main(String[] args) {
    new NodeRun("127.0.0.1", "127.0.0.6").invoke()
  }
}
</pre>

Examples of the use of the cluster_cli software library are available on GitHub at:

Mandelbrot Calculation

https://github.com/JonKerridge/ClusterMandelbrot

Creating a Concordance of Text Files

https://github.com/JonKerridge/ClusterConcordance

Please note that once an artifact comprising a jar that runs a node has been created,
it can be used to load ANY application.  The node loading capability does not need to be linked to
a specific application.

The following class will run a node using the loop back network capability based on addresses in 
the range 120.0.0.0.

<pre>
class RunNode {
  static void main(String[] args) {
    new NodeRun(args[0], args[1]).invoke()
  }
}
</pre>

assuming a jar exists with RunNode as entry point then

<pre>
java -jar RunNode.jar 127.0.0.1 127.0.0.5
</pre>

will create a node at IP address 127.0.0.5 that expects the host node to be running at 127.0.0.1.

When creating such a network the host node MUST be invoked first, followed by the cluster nodes.

The only difference when creating a node of a real cluster is that args[0] contains the IP address of the host
and there is no args[1].

Versions 1.0.4+ have been tested on a real network of workstations to form the cluster, 
using the Cluster Mandelbrot application (see https://github.com/JonKerridge/ClusterMandelbrot).

testing involved creating NetHost.jar and NetNode.jar artifacts which could then be 
transferred to the network nodes using Remote Desktop.  The nodes where NetNode is 
executed just needed that jar and are invoked as

<pre>
java -jar NetNode.jar hostip-address
</pre>

The host node required: NetHost.jar, the *.clistruct (files) and the folder 
containing the class definitions, copied from your IDE.

so the folder mandelbrot contained the class files Mandelbrotdata and MandelbrotCollect

the host is invoked  using, for example, omitting the .clistruct suffix

<pre>
java -jar NetHost.jar ./mandelbrot1n4w
</pre>

The host node MUST be started first and then the other nodes.
The nodes do NOT need access to the application class files as these are 
transferred as required from the host.





