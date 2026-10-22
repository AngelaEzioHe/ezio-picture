package com.yupi.yupicturebackend.api.aliyunai.model;

import cn.hutool.core.annotation.Alias;
import lombok.Data;
import java.io.Serializable;

/** 万相 2.1 四周扩图请求。 */
@Data
public class CreateOutPaintingTaskRequest implements Serializable {
    private String model = "wanx2.1-imageedit";
    private Input input;
    private Parameters parameters;

    @Data
    public static class Input {
        private String function = "expand";
        private String prompt = "保持原图主体、风格与光照，向四周自然延伸完整场景";
        @Alias("base_image_url")
        private String imageUrl;
    }

    @Data
    public static class Parameters implements Serializable {
        /** 各方向扩展比例，范围 [1.0, 2.0]。 */
        @Alias("top_scale")
        private Float topScale = 1.5f;
        @Alias("bottom_scale")
        private Float bottomScale = 1.5f;
        @Alias("left_scale")
        private Float leftScale = 1.5f;
        @Alias("right_scale")
        private Float rightScale = 1.5f;
    }
}
