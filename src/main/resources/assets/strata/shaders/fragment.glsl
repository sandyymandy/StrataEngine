#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color; // This might be empty if the model doesn't provide vertex colors

out vec4 out_Color;

uniform sampler2D u_Texture;
uniform bool u_UseTexture;

void main() {
    vec4 texColor = vec4(1.0);
    if (u_UseTexture) {
        texColor = texture(u_Texture, v_TexCoord);
    }

    // If v_Color is 0 (uninitialized), we default to white (1.0)
    vec4 tint = length(v_Color) > 0.0 ? v_Color : vec4(1.0);

    out_Color = texColor * tint;
}