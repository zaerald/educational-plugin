package com.jetbrains.edu.python.learning.codeforces;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.codeforces.run.CodeforcesRunConfiguration;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;

public class PyCodeforcesRunConfiguration extends PythonRunConfiguration implements CodeforcesRunConfiguration {
  public PyCodeforcesRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    if (!isRedirectInput() || getInputFile().isEmpty()) {
      throw new RuntimeConfigurationException("Redirect input is not set for Codeforces Run Configuration");
    }
  }

  @Override
  public void setExecutableFile(@NotNull VirtualFile file) {
    setScriptName(file.getPath());
  }
}
