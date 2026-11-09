// The stage follows the section being read, the slider walks the pause, and the download names the right file.
document.documentElement.classList.add("js");

const shots = [...document.querySelectorAll(".shot")];
// The pause section shows whichever stage its slider is on.
const show = (name) => {
  const wanted = name === "pause" ? STAGES[Number(slider.value)].shot : name;
  shots.forEach((shot) => {
    shot.classList.toggle("is-on", shot.dataset.shot === wanted);
    // The hidden screens are only see-through, so screen readers are told to skip them.
    shot.toggleAttribute("aria-hidden", shot.dataset.shot !== wanted);
  });
};

// The section nearest the middle of the screen chooses the picture on the stage.
const readers = new IntersectionObserver(
  (entries) => entries.forEach((entry) => entry.isIntersecting && show(entry.target.dataset.shot)),
  { rootMargin: "-45% 0px -45% 0px" },
);
document.querySelectorAll("[data-shot]:not(.shot)").forEach((section) => readers.observe(section));

// Each part of a section rises in turn as the section arrives, once.
const parts = document.querySelectorAll(".read > section > *, .module > *, .cta > *, .foot > *");
parts.forEach((part) => {
  part.classList.add("reveal");
  part.style.setProperty("--i", [...part.parentElement.children].indexOf(part) % 6);
});
const entrances = new IntersectionObserver(
  (entries) => entries.forEach((entry) => {
    if (!entry.isIntersecting) return;
    entry.target.classList.add("is-in");
    entrances.unobserve(entry.target);
  }),
  { rootMargin: "0px 0px -8% 0px" },
);
parts.forEach((part) => entrances.observe(part));

// The pause, stage by stage, with the app's own words. One phrase of the app's twelve, new each visit.
const PHRASES = [
  "I am choosing this, not drifting into it.",
  "This minute is mine, and I spend it on purpose.",
  "The urge will pass whether I open this or not.",
  "I can wait ten more minutes and decide again.",
  "Attention is the one thing I cannot get back.",
  "What I do right now is what my whole day becomes.",
  "I opened this on autopilot, and I am awake now.",
  "Boredom is not an emergency, and I can sit with it.",
  "I decide what deserves my attention today.",
  "A small pause now saves a long scroll later.",
  "I am the one who set this block, for a reason.",
  "Nothing in this feed is more urgent than my plan.",
];
const STAGES = [
  { clause: "First, a breath", shot: "breath", text: "Ten seconds. A circle grows as you breathe in and shrinks as you breathe out. The urge usually fades within a minute." },
  { clause: "Then, a reason", shot: "reason", text: "What do you need it for: work, a message, an errand, boredom, habit? Your answer is kept on the device, for your own look back." },
  { clause: "Then, the phrase", shot: "phrase", text: "Type a short phrase word by word. Strict sessions draw longer ones. Try one here." },
  { clause: "Last, how long", shot: "length", text: "Choose how long the app may stay open. Each pass ends on its own, and the next pause on the same day asks a little more." },
];
const slider = document.getElementById("stage");
const clause = document.querySelector(".pause-clause");
const text = document.querySelector(".pause-text");
const phoneShot = document.querySelector(".pause-shot img");
const names = document.querySelectorAll(".stage-names li");
const tryBox = document.querySelector(".try");
const phraseOut = document.querySelector(".try-phrase");
const phraseIn = document.getElementById("phrase-input");
const count = document.querySelector(".try-count");
const phrase = PHRASES[Math.floor(Math.random() * PHRASES.length)];
const words = phrase.split(" ");
phraseOut.textContent = phrase;
count.textContent = `0 of ${words.length} words`;

function stage(index) {
  const step = STAGES[index];
  clause.textContent = step.clause;
  text.textContent = step.text;
  slider.setAttribute("aria-valuetext", step.clause);
  slider.parentElement.style.setProperty("--passed", `${(index / 3) * 100}%`);
  names.forEach((name, i) => name.classList.toggle("is-on", i === index));
  tryBox.hidden = step.shot !== "phrase";
  phoneShot.src = document.querySelector(`.shot[data-shot="${step.shot}"]`).src;
  show(step.shot);
}
slider.addEventListener("input", () => stage(Number(slider.value)));

// As in the app: each word counts once it matches, in order, ignoring case and the full stop.
const plain = (word) => word.toLowerCase().replace(/[.,]/g, "");
phraseIn.addEventListener("input", () => {
  const typed = phraseIn.value.trim().split(/\s+/).filter(Boolean);
  let done = 0;
  while (done < typed.length && done < words.length && plain(typed[done]) === plain(words[done])) done++;
  const finished = done === words.length;
  count.textContent = finished ? "Done. In the app, you would now choose how long." : `${done} of ${words.length} words`;
  count.classList.toggle("is-done", finished);
});

// The download names the file for the chosen platform.
const FILES = {
  Android: ["the .apk", "for Android 8 and later."],
  Linux: ["the .deb", "for Ubuntu and Debian."],
  Windows: ["the .msi", "for Windows 11."],
};
document.querySelectorAll('.segmented input').forEach((choice) => choice.addEventListener("change", () => {
  const [kind, rest] = FILES[choice.value];
  document.querySelector(".dialog-kind").textContent = kind;
  document.querySelector(".dialog-file").lastChild.textContent = ` ${rest}`;
  document.querySelector(".dialog-os").textContent = choice.value;
}));
// Start on the visitor's own platform when it is one of the three.
const platform = /Android/i.test(navigator.userAgent) ? "Android" : /Windows/i.test(navigator.userAgent) ? "Windows" : /Linux/i.test(navigator.userAgent) ? "Linux" : null;
if (platform) {
  const choice = document.querySelector(`.segmented input[value="${platform}"]`);
  choice.checked = true;
  choice.dispatchEvent(new Event("change"));
}
