import java.lang.reflect.Method;
public class Inspect {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.minecraft.client.renderer.entity.layers.RenderLayer");
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println(m);
        }
        System.out.println("---");
        Class<?> modelClazz = Class.forName("net.minecraft.client.model.EntityModel");
        for (Method m : modelClazz.getDeclaredMethods()) {
            System.out.println(m);
        }
    }
}
