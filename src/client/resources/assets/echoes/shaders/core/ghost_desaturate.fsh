#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec4 normal;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    
    // Desaturate the texture color (luminance)
    float gray = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));
    
    // We apply the grayscale back, but keep the original texture alpha
    vec4 grayscaleColor = vec4(gray, gray, gray, texColor.a);
    
    // Now multiply by vertexColor (which has our ghost tint and alpha) and ColorModulator
    vec4 finalColor = grayscaleColor * vertexColor * ColorModulator;
    
    if (finalColor.a < 0.01) {
        discard;
    }
    
    fragColor = linear_fog(finalColor, vertexDistance, FogStart, FogEnd, FogColor);
}
