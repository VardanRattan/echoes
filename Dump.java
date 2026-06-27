import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import java.lang.reflect.Field;
public class Dump {
    public static void main(String[] args) throws Exception {
        for (Field f : AvatarRenderState.class.getFields()) {
            System.out.println(f.getName() + " : " + f.getType().getName());
        }
    }
}
