package ats.vacancy.template;

public record SkillKindHistory(int must, int nice, int optional) {

    public int total() {
        return must + nice + optional;
    }

    public boolean isEmpty() {
        return total() == 0;
    }
}
