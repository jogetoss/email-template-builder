package org.joget.etb;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.joget.etb.api.EmailBlock;
import org.joget.etb.lib.ColumnBlock;
import org.joget.etb.lib.RepeatBlock;
import org.joget.etb.lib.StepsBlock;
import org.joget.etb.lib.TextBlock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class BlockCatalogTest {

    @Test
    public void theSummarySaysWhereEachBlockMayGo() {
        List<EmailBlock> blocks = List.of(new TextBlock(), new ColumnBlock(), new RepeatBlock());
        JSONArray s = BlockCatalog.summary(blocks);
        assertEquals(3, s.length());
        JSONObject text = s.getJSONObject(0);
        assertEquals("org.joget.etb.lib.TextBlock", text.getString("className"));
        assertTrue(text.getJSONArray("allowedIn").toList().containsAll(List.of("template", "column", "repeat", "outside")));
        assertFalse(text.has("holdsBlocksAs"));
        assertEquals(List.of("row"), s.getJSONObject(1).getJSONArray("allowedIn").toList());
        assertEquals("repeat", s.getJSONObject(2).getString("holdsBlocksAs"));
    }

    @Test
    public void propertiesComeFromTheBlocksOwnPropertyPage() throws Exception {
        // Joget's property-editor JSON is lenient (unquoted keys, single quotes); read it as the builder does
        String page = new String(StepsBlock.class.getResourceAsStream("/properties/blocks/StepsBlock.json").readAllBytes(), StandardCharsets.UTF_8);
        JSONArray props = BlockCatalog.properties(page, new JSONObject(new StepsBlock().getDefaultPropertyValues()));
        JSONObject steps = find(props, "steps");
        assertEquals("grid", steps.getString("type"));
        assertEquals(List.of("label", "matches"), steps.getJSONArray("rowKeys").toList());
        JSONObject mark = find(props, "doneMark");
        assertEquals(List.of("check", "number"), mark.getJSONArray("values").toList());
        assertEquals("check", mark.get("default"));
        assertEquals(List.of("true", ""), find(props, "lastDone").getJSONArray("values").toList());
        assertTrue(find(props, "current").getString("description").length() <= 203);
        assertEquals("a broken page gives an empty list", 0, BlockCatalog.properties("[{", new JSONObject()).length());
    }

    static JSONObject find(JSONArray props, String name) {
        for (int i = 0; i < props.length(); i++) {
            if (name.equals(props.getJSONObject(i).getString("name"))) {
                return props.getJSONObject(i);
            }
        }
        fail("no property " + name);
        return null;
    }
}
