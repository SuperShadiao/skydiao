#version 330

// 玻璃合成哨兵元素的顶点着色器。
// 顶点坐标直接作为 NDC 使用，不依赖 gl_VertexID，因此即使 GuiRenderer 以非零
// baseVertex 绘制（多个 GUI 元素共享同一顶点缓冲）也能得到正确的全屏三角形。
in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}