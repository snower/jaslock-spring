package io.github.snower.jaslock.spring.example.pollingrequest.controller.backend;

import io.github.snower.jaslock.spring.example.pollingrequest.dto.CallRequestDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.dto.CallResponseDTO;
import io.github.snower.jaslock.spring.example.pollingrequest.service.PollingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;

@Slf4j
@Tag(name = "CallController", description = "云上调用接口")
@RestController
@RequestMapping("/backend/app")
public class CallController {

    @Autowired
    private PollingService pollingService;

    @GetMapping(value = "/v1/ping")
    @Operation(summary = "ping测试")
    public String handleCloudRequest(@RequestParam String appId) {
        CallRequestDTO callRequestDTO = new CallRequestDTO();
        callRequestDTO.setAppId(new ObjectId(appId));
        callRequestDTO.setBizType("STATUS");
        callRequestDTO.setMethod("ping");
        callRequestDTO.setPayload("PING");
        CallResponseDTO response = pollingService.requestTask(callRequestDTO);
        if (response.getCode() != 0) {
            throw new HttpServerErrorException(HttpStatusCode.valueOf(response.getCode()), response.getMsg());
        }
        return response.getData();
    }

    @PostMapping(value = "/v1/request")
    @Operation(summary = "处理云上请求")
    public CallResponseDTO handleCloudRequest(@RequestBody @Validated CallRequestDTO request) {
        return pollingService.requestTask(request);
    }

    @PostMapping(value = "/v1/push")
    @Operation(summary = "处理云上请求")
    public Boolean handleCloudPush(@RequestBody @Validated CallRequestDTO request) {
        pollingService.pushTask(request);
        return true;
    }
}
