package com.huawei.cse.porter.gateway;

import org.apache.servicecomb.core.Handler;
import org.apache.servicecomb.core.Invocation;
import org.apache.servicecomb.foundation.common.utils.JsonUtils;
import org.apache.servicecomb.provider.springmvc.reference.async.CseAsyncRestTemplate;
import org.apache.servicecomb.swagger.invocation.AsyncResponse;
import org.apache.servicecomb.swagger.invocation.Response;
import org.apache.servicecomb.swagger.invocation.exception.InvocationException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import com.huawei.cse.porter.user.api.SessionInfo;


public class AuthHandler implements Handler {
  private CseAsyncRestTemplate restTemplate = new CseAsyncRestTemplate();

  @Override
  public void handle(Invocation invocation, AsyncResponse asyncResponse) throws Exception {
    if (invocation.getMicroserviceName().equals("user-service")
        && (invocation.getOperationName().equals("login")
            || (invocation.getOperationName().equals("getSession")))) {
      // login： 直接返回认证结果。  开发者需要在JS里面设置cookie。 
      invocation.next(asyncResponse);
    } else {
      // check session
      String sessionId = invocation.getContext("session-id");
      if (sessionId == null) {
        throw new InvocationException(403, "", "session is not valid.");
      }

      // 在网关执行的Hanlder逻辑，是reactive模式的，不能使用阻塞调用。
      ListenableFuture<ResponseEntity<SessionInfo>> sessionInfoFuture =
          restTemplate.getForEntity("cse://user-service/v1/user/session?sessionId=" + sessionId, SessionInfo.class);
      sessionInfoFuture.addCallback(
          new ListenableFutureCallback<ResponseEntity<SessionInfo>>() {
            @Override
            public void onFailure(Throwable ex) {
              asyncResponse.complete(Response.failResp(new InvocationException(403, "", "session is not valid.")));
            }

            @Override
            public void onSuccess(ResponseEntity<SessionInfo> result) {
              SessionInfo sessionInfo = result.getBody();
              if (sessionInfo == null) {
                asyncResponse.complete(Response.failResp(new InvocationException(403, "", "session is not valid.")));
              }
              try {
                // 将会话信息传递给后面的微服务。后面的微服务可以从context获取到会话信息，从而可以进行鉴权等。 
                invocation.addContext("session-id", sessionId);
                invocation.addContext("session-info", JsonUtils.writeValueAsString(sessionInfo));
                invocation.next(asyncResponse);
              } catch (Exception e) {
                asyncResponse.complete(Response.failResp(new InvocationException(500, "", e.getMessage())));
              }
            }
          });
    }
  }
}
