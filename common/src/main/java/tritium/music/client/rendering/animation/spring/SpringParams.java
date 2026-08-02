package tritium.music.client.rendering.animation.spring;

public class SpringParams {
    double mass = 1.0;
    double damping = 10.0;
    double stiffness = 100.0;
    boolean soft = false;

    public SpringParams() {
    }

    public SpringParams(double mass, double damping, double stiffness, boolean soft) {
        this.mass = mass;
        this.damping = damping;
        this.stiffness = stiffness;
        this.soft = soft;
    }
}
