package com.yupi.yupicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yupi.yupicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunAiApi {
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // 可配置为北京业务空间的专属域名；创建和查询必须使用同一地址。
    @Value("${aliYunAi.baseUrl:https://dashscope.aliyuncs.com}")
    private String baseUrl = "https://dashscope.aliyuncs.com";

    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest request) {
        if (request == null || request.getInput() == null
                || StrUtil.isBlank(request.getInput().getImageUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "扩图图片不能为空");
        }
        CreateOutPaintingTaskRequest.Parameters parameters = request.getParameters();
        if (parameters == null) {
            parameters = new CreateOutPaintingTaskRequest.Parameters();
            request.setParameters(parameters);
        }
        validateScale(parameters.getTopScale());
        validateScale(parameters.getBottomScale());
        validateScale(parameters.getLeftScale());
        validateScale(parameters.getRightScale());
        // 固定为扩图，一次只生成一张图片。
        request.setModel("wanx2.1-imageedit");
        request.getInput().setFunction("expand");
        JSONObject payload = JSONUtil.parseObj(JSONUtil.toJsonStr(request));
        payload.getJSONObject("parameters").set("n", 1);
        try (HttpResponse response = HttpRequest.post(apiUrl("/services/aigc/image2image/image-synthesis"))
                .header("Authorization", "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .timeout(30000)
                .body(payload.toString()).execute()) {
            JSONObject body = checkResponse(response, "AI 扩图失败");
            CreateOutPaintingTaskResponse result = body.toBean(CreateOutPaintingTaskResponse.class);
            if (result.getOutput() == null || StrUtil.isBlank(result.getOutput().getTaskId())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图未返回任务 ID");
            }
            return result;
        }
    }

    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId) || !taskId.matches("[a-zA-Z0-9-]+")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务 ID 无效");
        }
        try (HttpResponse response = HttpRequest.get(apiUrl("/tasks/" + taskId))
                .header("Authorization", "Bearer " + apiKey)
                .timeout(30000).execute()) {
            GetOutPaintingTaskResponse result = checkResponse(response, "获取任务结果失败")
                    .toBean(GetOutPaintingTaskResponse.class);
            GetOutPaintingTaskResponse.Output output = result.getOutput();
            if (output == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图未返回任务状态");
            }
            if ("SUCCEEDED".equals(output.getTaskStatus())) {
                if (output.getResults() != null) {
                    for (GetOutPaintingTaskResponse.Result image : output.getResults()) {
                        if (StrUtil.isNotBlank(image.getUrl())) {
                            output.setOutputImageUrl(image.getUrl());
                            break;
                        }
                    }
                }
                if (StrUtil.isBlank(output.getOutputImageUrl())) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图未返回结果图片");
                }
            }
            return result;
        }
    }

    private String apiUrl(String path) {
        return StrUtil.removeSuffix(baseUrl, "/") + "/api/v1" + path;
    }

    private void validateScale(Float scale) {
        if (scale != null && (!Float.isFinite(scale) || scale < 1 || scale > 2)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "四周扩图比例必须在 1 到 2 之间");
        }
    }

    private JSONObject checkResponse(HttpResponse response, String message) {
        if (!JSONUtil.isTypeJSONObject(response.body())) {
            log.error("阿里云扩图响应异常，HTTP {}", response.getStatus());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, message + "，服务响应异常");
        }
        JSONObject body = JSONUtil.parseObj(response.body());
        if (!response.isOk() || StrUtil.isNotBlank(body.getStr("code"))) {
            log.error("阿里云扩图失败，HTTP {}，code={}，message={}，requestId={}",
                    response.getStatus(), body.getStr("code"), body.getStr("message"), body.getStr("request_id"));
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    message + "，" + body.getStr("code", "HTTP " + response.getStatus())
                            + "：" + body.getStr("message", "请稍后重试"));
        }
        return body;
    }
}
