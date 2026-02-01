#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 out_Color;

uniform sampler2D u_Texture;

void main() {
    vec4 texColor = texture(u_Texture, v_TexCoord);

    if (texColor.a < 0.1) {
        discard;
    }

    out_Color = texColor * v_Color;
}