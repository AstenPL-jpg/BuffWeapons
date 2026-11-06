package pl.buffweapons;

public enum ActivationMethod {
    SHIFT_F(1, "Shift + F", "Kucnij i wciśnij F (zamiana rąk), trzymając swoją broń w ręce."),
    SHIFT_Q(2, "Shift + Q", "Kucnij i wciśnij Q (wyrzucenie), trzymając swoją broń w ręce. Wtedy broń się nie wyrzuca."),
    COMMAND(4, "Komenda /uzyjultimate", "Wpisz /uzyjultimate, kiedy ultimate jest gotowe.");

    public final int bit;
    public final String title;
    public final String description;

    ActivationMethod(int bit, String title, String description) {
        this.bit = bit;
        this.title = title;
        this.description = description;
    }
}
