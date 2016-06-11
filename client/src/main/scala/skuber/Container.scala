package skuber

import java.net.URL
import java.util.Date

/**
 * @author David O'Riordan
 */

case class Container(
    name: String,
    image: String,
    command: List[String] = List(),
    args: List[String] = List(),
    workingDir: Option[String] = None,
    ports : List[Container.Port] = List(),
    env: List[EnvVar] = List(),
    resources: Option[Resource.Requirements] = None,
    volumeMounts: List[Volume.Mount] = List(),
    livenessProbe: Option[Probe] = None,
    readinessProbe: Option[Probe] = None,
    lifeCycle: Option[Lifecycle] = None,
    terminationMessagePath: String = "/var/log/termination",
    imagePullPolicy: Container.PullPolicy.Value = Container.PullPolicy.IfNotPresent,
    securityContext: Option[Security.Context] = None)
      extends Limitable
{
  def exposePort(p: Container.Port) : Container = this.copy(ports=p::this.ports)
  def exposePort(port: Int) : Container = exposePort(Container.Port(containerPort=port))

  def setEnvVar(n: String,v: String) = {
    import EnvVar.strToValue
    val envVar = EnvVar(n,v)
    this.copy(env = this.env :+ envVar)
  }
  def setEnvVarFromField(n: String, fieldPath: String) = {
    val envVar = EnvVar(n,EnvVar.FieldRef(fieldPath))
    this.copy(env = this.env :+ envVar)
  }

  def withWorkingDir(wd: String) = this.copy(workingDir = Some(wd))
  def withArgs(arg: String*) = this.copy(args = arg.toList)
  def withEntrypoint(cmd: String*) = this.copy(command=cmd.toList)
  def withTerminationMessagePath(t: String) = this.copy(terminationMessagePath= t)

  def limitCPU(cpu: Resource.Quantity) = addResourceLimit(Resource.cpu, cpu)
  def limitMemory(mem: Resource.Quantity) = addResourceLimit(Resource.memory, mem)
  def addResourceLimit(name: String, limit: Resource.Quantity): Container = {
    val currResources = this.resources.getOrElse(Resource.Requirements())
    val newLimits = currResources.limits + (name -> limit)
    this.copy(resources=Some(Resource.Requirements(newLimits, currResources.requests)))
  }

  def requestCPU(cpu: Resource.Quantity) = addResourceRequest(Resource.cpu, cpu)
  def requestMemory(mem: Resource.Quantity) = addResourceRequest(Resource.memory, mem)
  def addResourceRequest(name: String, req: Resource.Quantity): Container = {
    val currResources = this.resources.getOrElse(Resource.Requirements())
    val newReqs = currResources.requests + (name -> req)
    this.copy(resources=Some(Resource.Requirements(currResources.limits, newReqs)))
  }

  def mount(name: String, path: String, readOnly: Boolean = false) =
    this.copy(volumeMounts=Volume.Mount(name, path, readOnly) :: this.volumeMounts)

  def withImagePullPolicy(policy: Container.PullPolicy.Value) =
    this.copy(imagePullPolicy = policy)

  def withLivenessProbe(probe:Probe) =
    this.copy(livenessProbe=Some(probe))
  def withHttpLivenessProbe(
    path: String,
    port: NameablePort = 80,
    initialDelaySeconds:Int = 0,
    timeoutSeconds:Int = 0,
    schema: String = "HTTP") = {
    val handler = HTTPGetAction(port = port, path = path, schema = schema)
    val probe = Probe(handler, initialDelaySeconds, timeoutSeconds)
    withLivenessProbe(probe)
  }
  def withReadinessProbe(probe:Probe) =
    this.copy(livenessProbe=Some(probe))
  def withHttpReadinessProbe(
    path: String,
    port: NameablePort = 80,
    initialDelaySeconds:Int = 0,
    timeoutSeconds:Int = 0,
    schema: String = "HTTP") = {
    val handler = HTTPGetAction(port = port, path = path, schema = schema)
    val probe = Probe(handler, initialDelaySeconds, timeoutSeconds)
    withReadinessProbe(probe)
  }

  def onPostStartDoExec(cmds: List[String]) = {
    val exec = ExecAction(cmds)
    val currLC = lifeCycle.getOrElse(Lifecycle())
    val newLC = currLC.copy(postStart=Some(exec))
    this.copy(lifeCycle=Some(newLC))
  }
  def onPreStopDoExec(cmds: List[String]) = {
    val exec = ExecAction(cmds)
    val currLC = lifeCycle.getOrElse(Lifecycle())
    val newLC = currLC.copy(preStop = Some(exec))
    this.copy(lifeCycle=Some(newLC))
  }
  def onPostStartDoHTTPGet(path: String, port: NameablePort = 80, schema: String = "HTTP") = {
    val get = HTTPGetAction(path=path,port=port,schema = schema)
    val currLC = lifeCycle.getOrElse(Lifecycle())
    val newLC = currLC.copy(postStart=Some(get))
    this.copy(lifeCycle=Some(newLC))
  }
  def onPreStopDoHTTPGet(path: String, port: Int = 80,schema: String = "HTTP") = {
    val get = HTTPGetAction(path=path,port=port,schema = schema)
    val currLC = lifeCycle.getOrElse(Lifecycle())
    val newLC = currLC.copy(preStop = Some(get))
    this.copy(lifeCycle=Some(newLC))
  }
}
      
    
object Container {
  
  object PullPolicy extends Enumeration {
    type PullPolicy = Value
    val Always, Never, IfNotPresent = Value
  }
  
  case class Port(
      containerPort: Int,
      protocol: Protocol.Value=Protocol.TCP,
      name: String = "",
      hostIP: String = "",
      hostPort:Option[Int] = None)
       
  sealed trait State { def id: String }
  case class Waiting(reason: Option[String] = None) extends State { def id="waiting" }
  case class Running(startedAt: Option[Timestamp]) extends State { def id="running" }
  case class Terminated(
      exitCode: Int,
      signal: Option[Int] = None,
      reason: Option[String] = None,
      message: Option[String] = None ,
      startedAt: Option[Timestamp] = None,
      finishedAt: Option[Timestamp] = None,
      containerID: Option[String] = None)
    extends State { def id="terminated" }
  
  case class Status(
      name: String,
      ready: Boolean,
      restartCount: Int,
      image: String,
      imageID: String,
      state: Option[State] = None,
      lastState: Option[State] = None,
      containerID: Option[String] = None) 
      
}    