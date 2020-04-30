package com.jetbrains.edu.javascript.learning.checkio.connectors;

import com.jetbrains.edu.javascript.learning.checkio.utils.JsCheckiONames;
import com.jetbrains.edu.learning.checkio.connectors.CheckiOOAuthRestService;
import com.jetbrains.edu.learning.checkio.utils.CheckiONames;
import org.jetbrains.annotations.NotNull;

public class JsCheckiOOAuthRestService extends CheckiOOAuthRestService {
  protected JsCheckiOOAuthRestService() {
    super(
      CheckiONames.JS_CHECKIO,
      JsCheckiONames.JS_CHECKIO_OAUTH_SERVICE_PATH,
      JsCheckiOOAuthConnector.getInstance()
    );
  }

  @NotNull
  @Override
  protected String getServiceName() {
    return JsCheckiONames.JS_CHECKIO_OAUTH_SERVICE_NAME;
  }
}
