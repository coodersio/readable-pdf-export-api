package io.cooders.readablepdf.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SvgTextLayerParserTest {

    private final SvgTextLayerParser parser = new SvgTextLayerParser();

    @Test
    void parsesTextAndTspanBaselinePositions() {
        String svg = """
                <svg width="300" height="200" xmlns="http://www.w3.org/2000/svg">
                  <g transform="translate(10 20)">
                    <text x="30" y="40" font-size="16" letter-spacing="1.5" fill="#336699">
                      <tspan x="30" y="40">Hello</tspan>
                      <tspan x="30" y="60">World</tspan>
                    </text>
                  </g>
                </svg>
                """;

        List<SvgTextLayerParser.SvgTextItem> items = parser.parse(svg);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).text()).isEqualTo("Hello");
        assertThat(items.get(0).x()).isEqualTo(40);
        assertThat(items.get(0).y()).isEqualTo(60);
        assertThat(items.get(0).fontSize()).isEqualTo(16);
        assertThat(items.get(0).letterSpacing()).isEqualTo(1.5);
        assertThat(items.get(0).horizontalScale()).isEqualTo(1);
        assertThat(items.get(0).verticalScale()).isEqualTo(1);
        assertThat(items.get(1).text()).isEqualTo("World");
        assertThat(items.get(1).x()).isEqualTo(40);
        assertThat(items.get(1).y()).isEqualTo(80);
    }

    @Test
    void keepsSvgTransformMatrixForTextRendering() {
        String svg = """
                <svg width="300" height="200" xmlns="http://www.w3.org/2000/svg">
                  <g transform="scale(2 3)">
                    <text x="10" y="20" font-size="10">Scaled</text>
                  </g>
                </svg>
                """;

        List<SvgTextLayerParser.SvgTextItem> items = parser.parse(svg);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).x()).isEqualTo(20);
        assertThat(items.get(0).y()).isEqualTo(60);
        assertThat(items.get(0).horizontalScale()).isEqualTo(2);
        assertThat(items.get(0).verticalScale()).isEqualTo(3);
        assertThat(items.get(0).normalizedA()).isEqualTo(1);
        assertThat(items.get(0).normalizedD()).isEqualTo(1);
    }
}
