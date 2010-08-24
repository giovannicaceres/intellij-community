package com.jetbrains.python.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Leonid Shalupov
 */
public abstract class PythonCommandLineState extends CommandLineState {

  // command line has a number of fixed groups of parameters; patchers should only operate on them and not the raw list.

  public static final String GROUP_EXE_OPTIONS = "Exe Options";
  public static final String GROUP_DEBUGGER = "Debugger";
  public static final String GROUP_SCRIPT = "Script";
  private final AbstractPythonRunConfiguration myConfig;
  private final List<Filter> myFilters;

  public AbstractPythonRunConfiguration getConfig() {
    return myConfig;
  }

  public PythonCommandLineState(AbstractPythonRunConfiguration runConfiguration, ExecutionEnvironment env, List<Filter> filters) {
    super(env);
    myConfig = runConfiguration;
    myFilters = filters;
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    return execute(executor, (CommandLinePatcher[])null);
  }

  public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(patchers);
    final ConsoleView console = createAndAttachConsole(getConfig().getProject(), processHandler, executor);

    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor) throws ExecutionException {
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    for (Filter filter : myFilters) {
      consoleBuilder.addFilter(filter);
    }
    
    final ConsoleView consoleView = consoleBuilder.getConsole();
    consoleView.attachToProcess(processHandler);
    return consoleView;
  }

  protected ColoredProcessHandler startProcess() throws ExecutionException {
    return startProcess(null);
  }

  /**
   * Patches the command line parameters applying patchers from first to last, and then runs it.
   * @param patchers any number of patchers; any patcher may be null, and the whole argument may be null.
   * @return handler of the started process
   * @throws ExecutionException
   */
  protected ColoredProcessHandler startProcess(CommandLinePatcher... patchers) throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine(patchers);

    final ColoredProcessHandler processHandler = doCreateProcess(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  public GeneralCommandLine generateCommandLine(CommandLinePatcher[] patchers) throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine();
    if (patchers != null) {
      for (CommandLinePatcher patcher: patchers) {
        if (patcher != null) patcher.patchCommandLine(commandLine);
      }
    }
    return commandLine;
  }

  protected ColoredProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    return createProcessHandler(commandLine.createProcess(), commandLine);
  }

  protected ColoredProcessHandler createProcessHandler(Process process, GeneralCommandLine commandLine) throws ExecutionException {
    return new ColoredProcessHandler(process, commandLine.getCommandLineString());
  }

  public GeneralCommandLine generateCommandLine() throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine();

    setRunnerPath(commandLine);

    // define groups
    ParametersList params = commandLine.getParametersList();
    params.addParamsGroup(GROUP_EXE_OPTIONS);
    params.addParamsGroup(GROUP_DEBUGGER);
    params.addParamsGroup(GROUP_SCRIPT);

    buildCommandLineParameters(commandLine);

    initEnvironment(commandLine);
    return commandLine;
  }

  protected void initEnvironment(GeneralCommandLine commandLine) {
    Map<String, String> envs = myConfig.getEnvs();
    if (envs == null)
      envs = new HashMap<String, String>();
    else
      envs = new HashMap<String, String>(envs);

    addPredefinedEnvironmentVariables(envs);
    commandLine.setEnvParams(envs);
    commandLine.setPassParentEnvs(myConfig.isPassParentEnvs());
  }

  protected void addPredefinedEnvironmentVariables(Map<String, String> envs) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(myConfig.getInterpreterPath());
    if (flavor != null) {
      flavor.addPredefinedEnvironmentVariables(envs);
    }
  }

  protected void setRunnerPath(GeneralCommandLine commandLine) throws ExecutionException {
    String interpreterPath = getInterpreterPath();
    commandLine.setExePath(interpreterPath);
  }

  protected String getInterpreterPath() throws ExecutionException {
    String interpreterPath = myConfig.getInterpreterPath();
    if (interpreterPath == null) {
      throw new ExecutionException("Cannot find Python interpreter for this run configuration");
    }
    return interpreterPath;
  }

  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
  }
}
