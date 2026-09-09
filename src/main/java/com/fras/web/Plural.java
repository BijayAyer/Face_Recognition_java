package com.fras.web;

/**
 * One noun, correctly numbered, for the sentences the Academic Setup delete
 * endpoints put in front of a person.
 *
 * <p>A file for two three-line methods needs a word of defence. The alternative
 * was the same lines copied into four controllers, or a message written to
 * sidestep the problem - "still has semesters: 1" - which is the kind of
 * phrasing that tells the reader a machine wrote it.
 *
 * <p>There is no attempt to pluralise English in general. Most of the nouns here
 * take an "s"; "class" does not, so its plural is passed in. A rule that guessed
 * would be wrong on a word nobody has written yet, and wrong in a message a user
 * reads.
 */
final class Plural {

    private Plural() {
    }

    /** {@code of(1, "semester")} is "1 semester"; {@code of(3, ..)} is "3 semesters". */
    static String of(long many, String noun) {
        return of(many, noun, noun + "s");
    }

    /** For the ones that do not simply take an "s": {@code of(3, "class", "classes")}. */
    static String of(long many, String one, String more) {
        return many + " " + (many == 1 ? one : more);
    }
}
