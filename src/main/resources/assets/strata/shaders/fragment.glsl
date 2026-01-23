#version 330 core

in vec2 pass_texCoord;
in vec4 pass_color; // This might be empty if the model doesn't provide vertex colors

out vec4 out_Color;

uniform sampler2D u_Texture;
uniform bool u_UseTexture;

void main() {
    vec4 texColor = vec4(1.0);
    if (u_UseTexture) {
        texColor = texture(u_Texture, pass_texCoord);
    }

    // If pass_color is 0 (uninitialized), we default to white (1.0)
    vec4 tint = length(pass_color) > 0.0 ? pass_color : vec4(1.0);

    out_Color = texColor * tint;
}