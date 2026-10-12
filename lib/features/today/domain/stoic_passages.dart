import 'dart:math';

class StoicPassage {
  final String id;
  final String title;
  final String author;
  final String category;
  final String text;

  const StoicPassage({
    required this.id,
    required this.title,
    required this.author,
    required this.category,
    required this.text,
  });

  int get wordCount => text.trim().split(RegExp(r'\s+')).length;
}

class StoicPassages {
  static final Random _random = Random();

  static const List<StoicPassage> all = [
    StoicPassage(
      id: 'seneca_time_1',
      title: 'On the Shortness of Life',
      author: 'Seneca',
      category: 'Time & Attention',
      text:
          'It is not that we have a short time to live, but that we waste a lot of it. Life is long enough, and a sufficiently generous estimate has been given to us for the highest achievements, if it were all well invested.',
    ),
    StoicPassage(
      id: 'marcus_morning_1',
      title: 'The Morning Mindset',
      author: 'Marcus Aurelius',
      category: 'Duty & Discipline',
      text:
          'At dawn, when you have trouble getting out of bed, tell yourself: I have to go to work as a human being. What do I have to complain of, if I am going to do what I was born for?',
    ),
    StoicPassage(
      id: 'epictetus_control_1',
      title: 'The Dichotomy of Control',
      author: 'Epictetus',
      category: 'Inner Sovereignty',
      text:
          'Some things are in our control and others not. Things in our control are opinion, pursuit, desire, aversion, and whatever are our own actions. Things not in our control are body, property, reputation, command, and whatever are not our own actions.',
    ),
    StoicPassage(
      id: 'seneca_tranquility_1',
      title: 'The Wandering Mind',
      author: 'Seneca',
      category: 'Focus & Tranquility',
      text:
          'To be everywhere is to be nowhere. People who spend their whole life traveling end up having plenty of places where they can find hospitality, but no real friendships.',
    ),
    StoicPassage(
      id: 'marcus_present_1',
      title: 'The Present Moment',
      author: 'Marcus Aurelius',
      category: 'Presence & Focus',
      text:
          'Do not disturb yourself by picturing your life as a whole; do not assemble in your mind the many and varied troubles which have come to you or may come. In every present circumstance ask yourself: What is there in this that is unbearable?',
    ),
    StoicPassage(
      id: 'epictetus_progress_1',
      title: 'The Cost of Growth',
      author: 'Epictetus',
      category: 'Deliberate Effort',
      text:
          'If you want to improve, be content to be thought foolish and stupid with regard to external things. Do not wish to be thought to know anything; and even if you appear to be somebody important to others, distrust yourself.',
    ),
    StoicPassage(
      id: 'deepwork_newport_1',
      title: 'The Depth Hypothesis',
      author: 'Cal Newport',
      category: 'Deep Work',
      text:
          'To produce at your peak level you need to work for extended periods with full concentration on a single task free from distraction. Put another way, the type of work that optimizes your performance is deep work.',
    ),
    StoicPassage(
      id: 'marcus_retreat_1',
      title: 'The Inner Sanctuary',
      author: 'Marcus Aurelius',
      category: 'Mindfulness',
      text:
          'Men seek retreats for themselves, houses in the country, seashores, and mountains. Nowhere can man find a quieter or more untroubled retreat than in his own soul. Constantly give yourself this retreat, and renew yourself.',
    ),
    StoicPassage(
      id: 'seneca_busy_1',
      title: 'The Illusion of Busyness',
      author: 'Seneca',
      category: 'Time & Attention',
      text:
          'No activity can be successfully pursued by an individual who is preoccupied since the mind, when distracted, absorbs nothing deeply. Nothing is less the business of the preoccupied man than living.',
    ),
    StoicPassage(
      id: 'epictetus_desire_1',
      title: 'Freedom from Craving',
      author: 'Epictetus',
      category: 'Self-Restraint',
      text:
          'Freedom is the only worthy goal in life. It is won by disregarding things that lie beyond our control. Do not crave what belongs to another, lest you become their slave.',
    ),
    StoicPassage(
      id: 'marcus_obstacle_1',
      title: 'The Obstacle Is the Way',
      author: 'Marcus Aurelius',
      category: 'Resilience',
      text:
          'The mind adapts and converts to its own purposes the obstacle to our acting. The impediment to action advances action. What stands in the way becomes the way.',
    ),
    StoicPassage(
      id: 'newport_boredom_1',
      title: 'Embracing Boredom',
      author: 'Cal Newport',
      category: 'Deep Work',
      text:
          'If every moment of potential boredom in your life is relieved with a quick glance at your phone, then your brain has likely been rewired such that it is not ready for deep work, even if you regularly schedule time to practice.',
    ),
    StoicPassage(
      id: 'seneca_clarity_1',
      title: 'On Purposeful Action',
      author: 'Seneca',
      category: 'Inner Sovereignty',
      text:
          'If one does not know to which port one is sailing, no wind is favorable. We are more often frightened than hurt; and we suffer more from imagination than from reality.',
    ),
    StoicPassage(
      id: 'marcus_discipline_1',
      title: 'Quiet Concentration',
      author: 'Marcus Aurelius',
      category: 'Duty & Discipline',
      text:
          'Concentrate every minute like a Roman—like a man—on doing what is in front of you with precise and genuine seriousness, tenderly, willingly, with justice. And on freeing yourself from all other distractions.',
    ),
    StoicPassage(
      id: 'epictetus_habits_1',
      title: 'How Habits Are Formed',
      author: 'Epictetus',
      category: 'Habit Mastery',
      text:
          'Every habit and capability is confirmed and grown in its corresponding actions: walking by walking, and running by running. Therefore, when you would make anything a habit, do it; when you would not make it a habit, do not do it.',
    ),
    StoicPassage(
      id: 'csikszentmihalyi_flow_1',
      title: 'The Order of Consciousness',
      author: 'Mihaly Csikszentmihalyi',
      category: 'Flow State',
      text:
          'Control of consciousness determines the quality of life. The optimal state of inner experience is one in which there is order in consciousness. This happens when psychic energy—or attention—is invested in realistic goals.',
    ),
    StoicPassage(
      id: 'seneca_delay_1',
      title: 'The Peril of Delay',
      author: 'Seneca',
      category: 'Urgency & Action',
      text:
          'Putting things off is the biggest waste of life: it snatches away each day as it comes, and denies us the present by promising the future. The greatest obstacle to living is expectancy, which hangs upon tomorrow and loses today.',
    ),
    StoicPassage(
      id: 'marcus_opinion_1',
      title: 'Mastery Over Judgment',
      author: 'Marcus Aurelius',
      category: 'Emotional Fortitude',
      text:
          'You have power over your mind, not outside events. Realize this, and you will find strength. Discard your misperceptions; stop being jerked like a puppet; limit yourself to the present.',
    ),
    StoicPassage(
      id: 'epictetus_intention_1',
      title: 'The Intentional Hour',
      author: 'Epictetus',
      category: 'Inner Sovereignty',
      text:
          'How long are you going to wait before you demand the best for yourself, and in no instance bypass the discriminations of reason? You are no longer a boy, but a full-grown man. If you are careless and lazy now, you will live and die in mediocrity.',
    ),
    StoicPassage(
      id: 'musonius_labor_1',
      title: 'Endurance of Labor',
      author: 'Musonius Rufus',
      category: 'Duty & Discipline',
      text:
          'If you accomplish something good with hard work, the labor passes quickly, but the good endures; if you do something shameful in pursuit of pleasure, the pleasure passes quickly, but the shame endures.',
    ),
    StoicPassage(
      id: 'marcus_simplicity_1',
      title: 'The Test of Essentiality',
      author: 'Marcus Aurelius',
      category: 'Essentialism',
      text:
          'Ask yourself at every moment: Is this necessary? For most of what we say and do is not essential. If you can eliminate it, you will have more time and more tranquility.',
    ),
    StoicPassage(
      id: 'seneca_wealth_1',
      title: 'True Wealth',
      author: 'Seneca',
      category: 'Mindfulness',
      text:
          'It is not the man who has too little, but the man who craves more, that is poor. Contentment with little is great wealth, for nature requires very little, while greed never has enough.',
    ),
    StoicPassage(
      id: 'holiday_stillness_1',
      title: 'Stillness is the Key',
      author: 'Ryan Holiday',
      category: 'Tranquility',
      text:
          'Stillness is what aims the archer’s arrow. It inspires new ideas. It sharpens perspective and stokes creativity. It makes room for gratitude and love. It is the key to clarity and accomplishment.',
    ),
    StoicPassage(
      id: 'epictetus_reaction_1',
      title: 'Ruling Your Reactions',
      author: 'Epictetus',
      category: 'Inner Sovereignty',
      text:
          'It is not events that upset people, but the view they take of them. When we are hindered, or disturbed, or grieved, let us never attribute it to others, but to ourselves; that is, to our own convictions.',
    ),
    StoicPassage(
      id: 'marcus_directness_1',
      title: 'Direct and Clean Action',
      author: 'Marcus Aurelius',
      category: 'Presence & Focus',
      text:
          'Waste no more time arguing about what a good man should be. Be one. Straighten your course and run the race directly, without distraction or useless hesitation.',
    ),
    StoicPassage(
      id: 'seneca_mastery_1',
      title: 'Self-Command',
      author: 'Seneca',
      category: 'Self-Restraint',
      text:
          'Most powerful is he who has himself in his own power. To command oneself is the greatest empire. Do not let impulsive desires steer the chariot of your actions.',
    ),
    StoicPassage(
      id: 'newport_craft_1',
      title: 'The Craftsman Mindset',
      author: 'Cal Newport',
      category: 'Deep Work',
      text:
          'The craftsman mindset focuses on what you can offer the world, whereas the passion mindset focuses on what the world can offer you. Dedication to deep craftsmanship builds rare and valuable skills.',
    ),
    StoicPassage(
      id: 'marcus_brevity_1',
      title: 'The Fleeting Nature of Praise',
      author: 'Marcus Aurelius',
      category: 'Inner Sovereignty',
      text:
          'Everything is ephemeral—both the one who remembers and the thing remembered. Value only that which stays true to reason, justice, and deliberate purpose.',
    ),
    StoicPassage(
      id: 'epictetus_vigilance_1',
      title: 'Vigilance over Thought',
      author: 'Epictetus',
      category: 'Mindfulness',
      text:
          'When an impulsive thought presents itself, challenge it immediately: You are just an impression, and not at all what you claim to represent. Test it against your core principles.',
    ),
    StoicPassage(
      id: 'seneca_firmness_1',
      title: 'The Firm Anchor',
      author: 'Seneca',
      category: 'Resilience',
      text:
          'A gem cannot be polished without friction, nor a man perfected without trials. Anchor your will against the storms of passing impulses and remain steadfast.',
    ),
  ];

  static StoicPassage getRandom([Random? random]) {
    final r = random ?? _random;
    return all[r.nextInt(all.length)];
  }

  static StoicPassage getById(String id) {
    return all.firstWhere(
      (p) => p.id == id,
      orElse: () => all.first,
    );
  }
}
