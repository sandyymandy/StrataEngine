#version 330 core

// Inputs from vertex shader
in vec2  v_TexCoord;
in float v_Layer;
in float v_Brightness;

// Texture array — replaces the old sampler2D atlas
uniform sampler2DArray u_Texture;

// Output
out vec4 FragColor;

void main() {
    // Sample from the correct layer of the texture array.
    // The third coordinate selects the layer (automatically rounded by the driver).
    vec4 texColor = texture(u_Texture, vec3(v_TexCoord, v_Layer));

    // Discard fully transparent pixels
    if (texColor.a < 0.1) {
        discard;
    }

    // Simple directional brightness — 1.0 on top faces, down to ~0.7 on sides
    vec3 litColor = texColor.rgb * v_Brightness;

    FragColor = vec4(litColor, texColor.a);
}
