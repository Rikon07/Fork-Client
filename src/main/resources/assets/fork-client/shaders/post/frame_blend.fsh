#version 330

uniform sampler2D LiveFrameSampler;
uniform sampler2D HistoryFrameSampler;

layout(std140) uniform BlurPlusParams {
    float HistoryWeight;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 currentSample = texture(LiveFrameSampler, texCoord);
    vec4 previousSample = texture(HistoryFrameSampler, texCoord);

    vec3 color = mix(currentSample.rgb, previousSample.rgb, HistoryWeight);
    fragColor = vec4(color, 1.0);
}
