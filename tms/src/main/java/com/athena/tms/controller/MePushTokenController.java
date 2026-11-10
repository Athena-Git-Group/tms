package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.PushTokenRegisterRequest;
import com.athena.tms.dto.PushTokenRevokeRequest;
import com.athena.tms.service.PushDeviceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人推播地址的登記與解除，對應 contracts 員工帳號與登入.yaml 的 registerPushToken
 * （{@code PUT /me/push-token}）與 revokePushToken（{@code DELETE /me/push-token}）。
 * 身分一律取自登入憑證，請求裡不帶員工 id，所以員工只能動自己的裝置。
 */
@RestController
@RequestMapping("/api/v1")
public class MePushTokenController {

    private final PushDeviceService pushDeviceService;

    public MePushTokenController(PushDeviceService pushDeviceService) {
        this.pushDeviceService = pushDeviceService;
    }

    @PutMapping("/me/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@RequestBody PushTokenRegisterRequest request) {
        CurrentActor actor = CurrentActor.require();
        pushDeviceService.register(TenantContext.require(), actor.staffId(), request);
    }

    @DeleteMapping("/me/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestBody PushTokenRevokeRequest request) {
        CurrentActor actor = CurrentActor.require();
        pushDeviceService.revoke(
                TenantContext.require(), actor.staffId(), request == null ? null : request.deviceFingerprint());
    }
}
