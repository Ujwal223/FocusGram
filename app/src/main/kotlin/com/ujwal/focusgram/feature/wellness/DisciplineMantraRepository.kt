package com.ujwal.focusgram.feature.wellness

/**
 * Repository of inspiring discipline mantras grouped by word count (10, 15, 20, 30, 50).
 *
 * Each word count list contains at least 30 unique sentences crafted to match the exact word count.
 * Shuffled non-repeating queues ensure sentences cycle randomly without duplicates until all
 * items for that word count are exhausted.
 */
object DisciplineMantraRepository {
    private val unexhaustedPools = mutableMapOf<Int, MutableList<Int>>()

    val mantras10: List<String> = listOf(
        "I control my attention and choose presence over endless scrolling.",
        "My focus is valuable and I will not waste it.",
        "Real life is unfolding outside this small illuminated digital screen.",
        "I enter this application with clear purpose and calm awareness.",
        "Discipline creates genuine freedom and shields my precious daily energy.",
        "I will not let an algorithm dictate my mental state.",
        "Calm awareness guides every action I take in this moment.",
        "I master my digital habits instead of serving shallow impulses.",
        "Every conscious breath returns my focus to what truly matters.",
        "I step back from mindless consumption to protect my peace.",
        "Intentional choices today build the strong discipline I need tomorrow.",
        "My peace of mind is worth more than digital approval.",
        "I observe the impulse to scroll and calmly let go.",
        "True clarity emerges when I silence constant unnecessary digital noise.",
        "I am fully present in reality rather than lost online.",
        "Deep focus is a skill that I practice with intention.",
        "I honor my time by choosing deliberate focus over distraction.",
        "Today I cultivate mental clarity and resist shallow algorithmic dopamine.",
        "My attention belongs to my goals and my real life.",
        "One intentional minute is worth hours of mindless social browsing.",
        "I choose to disconnect and protect my inner mental sanctuary.",
        "Quiet discipline today creates meaningful accomplishments for my future self.",
        "I consume information intentionally and never let feeds consume me.",
        "Patience and awareness empower me to control every digital impulse.",
        "I value deep concentration over brief moments of fleeting novelty.",
        "Mindful living begins with guarding how I spend my time.",
        "I release the urge to seek superficial digital social validation.",
        "Focused effort on what matters will transform my personal journey.",
        "I pause, breathe deeply, and direct my energy toward purpose.",
        "My daily habits shape my destiny and I choose presence.",
    )

    val mantras15: List<String> = listOf(
        "I refuse to surrender my sacred attention to an algorithm designed for endless addictive engagement.",
        "True inner power begins the moment I decide to control my own digital screen time.",
        "My creative potential flourishes only when I consciously protect my mind from shallow continuous distractions.",
        "I use social platforms as intentional tools rather than allowing them to dictate my life.",
        "Every mindful pause between urges strengthens my willpower and deepens my everyday clear mental clarity.",
        "Distraction is costly, but deliberate attention yields profound focus and peaceful personal accomplishments every day.",
        "I honor the precious hours of my day by doing real work in physical reality.",
        "Stepping away from superficial feeds allows my genuine creativity and deeper focus to freely flourish.",
        "I am the conscious gatekeeper of my mind and refuse to indulge impulsive online consumption.",
        "Living intentionally means knowing exactly when to close this screen and return to real life.",
        "A disciplined mind navigates the modern world with supreme grace, calm purpose, and unwavering resolve.",
        "I choose conscious self-restraint over effortless dopamine hits that leave me feeling exhausted and unfocused.",
        "My highest aspirations require undivided daily focus that no superficial digital feed can ever provide.",
        "Peace comes from deliberate restraint and prioritizing meaningful relationships over endless virtual feeds and notifications.",
        "I master this digital technology with disciplined poise instead of letting it dictate my day.",
        "Clear intention guides my browsing so I leave this app fulfilled rather than quietly depleted.",
        "Every second given to shallow feeds is stolen from my greatest personal goals and dreams.",
        "I choose to experience reality directly rather than watching others live through digital screens today.",
        "Self-control is a daily practice that transforms restless impulses into unshakable inner focus and clarity.",
        "I cultivate deep concentration today by saying a firm no to cheap, endless online distractions.",
        "My presence of mind is my greatest superpower in a world full of noisy distractions.",
        "Deliberate choices protect my energy, allowing me to build a truly remarkable and focused life.",
        "I release the compulsive urge to refresh and reclaim full ownership of my valuable attention.",
        "Digital serenity begins when I silence unnecessary notifications and focus deeply on what truly matters.",
        "I am committed to living deeply in the present moment without seeking empty digital validation.",
        "When I conquer the impulse to scroll, I unlock hours of deep, rewarding creative work.",
        "My attention is sacred, so I guard it against addictive algorithmic loops with fierce discipline.",
        "I deliberately choose silence and deep contemplation over the endless roar of modern social media.",
        "Intentional habits build the quiet confidence and mental strength necessary to achieve lasting personal greatness.",
        "I step forward with conscious purpose, leaving behind mindless scrolling to embrace real world living.",
    )

    val mantras20: List<String> = listOf(
        "By choosing mindful focus over endless scrolling, I actively reclaim the time, energy, and mental peace needed for my goals.",
        "I refuse to let engineered digital feeds capture my thoughts when genuine life experiences are waiting for me outside today.",
        "Discipline is the ultimate bridge between empty intentions and real accomplishments, so I choose deliberate focus over mindless digital consumption.",
        "Technology should elevate my daily existence rather than trap my attention in an endless loop of superficial and fleeting dopamine.",
        "I take complete responsibility for where I place my attention, choosing intentional presence over reactive digital browsing every single hour.",
        "When I pause and breathe, I regain conscious command over my impulses and remember what truly matters in my life.",
        "Every moment spent away from algorithmic feeds is an investment in my mental health, personal relationships, and deep creative clarity.",
        "I honor my mind by consuming only what nourishes my intellect, refusing to waste precious hours on shallow social noise.",
        "The world needs my authentic contribution, not my passive consumption of endless videos and superficial images on a small screen.",
        "Mastering my digital impulses gives me the freedom to think deeply, create meaningful work, and live with genuine peace today.",
        "I step into this digital space with absolute clarity of purpose, determined to leave as soon as my task finishes.",
        "True satisfaction comes from real-world accomplishments and heartfelt human connections, never from the synthetic validation of social media engagement metrics.",
        "I protect my mental clarity with unwavering discipline, knowing that my attention determines the entire quality of my everyday experience.",
        "A calm and focused mind is capable of extraordinary achievements when freed from the constant pull of compulsive digital notifications.",
        "I practice healthy boundaries with my smartphone so that my creative energy flows freely toward the projects that inspire me.",
        "Mindful living requires conscious resistance against the addictive pull of endless feeds designed to keep my mind permanently scattered today.",
        "I choose to be the active creator of my life rather than a passive observer of someone else's curated reality.",
        "The discipline I practice right now strengthens my character and preserves the precious hours I need for personal growth today.",
        "I reject the illusion of continuous connectivity in favor of deep presence, quiet contemplation, and intentional daily productive action now.",
        "My daily focus is far too valuable to be traded away for fleeting entertainment and manipulative advertising on algorithmic platforms.",
        "I cultivate stillness amidst modern digital chaos, ensuring that my mind remains calm, centered, and deeply focused on my priorities.",
        "Every conscious decision to close an addictive feed reinforces my self-respect and empowers me to live with genuine intentionality today.",
        "I direct my energy toward meaningful pursuits that endure, letting go of the constant urge to check trivial digital updates.",
        "True freedom is the ability to direct my attention wherever I choose without being pulled by compulsive digital habits today.",
        "I respect my future self by making disciplined choices now, refusing to sacrifice long-term fulfillment for brief moments of amusement.",
        "My peace of mind flourishes when I disconnect from endless feeds and reconnect with the quiet beauty of physical reality.",
        "I choose to be fully awake to my own life rather than mesmerized by the glowing pixels of my screen.",
        "Clear boundaries around my digital consumption allow my thoughts to deepen and my authentic voice to emerge with great clarity.",
        "I possess the strength to resist shallow distractions and channel my focused attention into work that makes a real difference.",
        "With every deliberate breath, I anchor myself in the present moment and release all desire for mindless online scrolling today.",
    )

    val mantras30: List<String> = listOf(
        "I choose to control my focus and not let algorithmic feeds govern my attention. Real life happens outside this screen, and my precious time deserves deliberate purpose and disciplined presence.",
        "Technology is a tool for my growth, not a trap for dopamine. I enter with conscious intent, observe with clear detachment, and leave before endless scrolling steals my peaceful day.",
        "True freedom comes from voluntary discipline. I possess mastery over my impulses, refusing to mindlessly consume superficial digital noise when real-world accomplishments demand my undivided attention and creative energy today.",
        "My attention is my most precious asset, shaping what I experience. I refuse to trade it away for mindless videos, choosing instead to invest in my own genuine personal growth.",
        "When I silence the constant clamor of digital notifications, I discover deep calm clarity. I step away from endless scrolling to protect my mental health, focus, and inner peace today.",
        "Every time I choose deliberate focus over compulsive browsing, my discipline grows stronger. I am the conscious master of my habits, and I decide where my precious attention flows today.",
        "The modern world is designed to fragment attention, but I choose concentration. I will not sacrifice my greatest ambitions on the altar of cheap, fleeting digital distractions in this moment.",
        "I enter this app with a clear, singular objective. Once finished, I will promptly close it, returning to the rich and tangible reality of my actual daily life and responsibilities.",
        "Mindful living means recognizing when an impulse does not serve purpose. I observe the urge to scroll, breathe through restlessness, and consciously return to what truly matters most in life.",
        "Superficial metrics and endless feeds cannot bring lasting fulfillment. I choose joy in authentic human connections, deep creative work, and the quiet satisfaction of meaningful personal daily accomplishments every day.",
        "I guard my focus against manipulative algorithms seeking to monetize time. My life is far too precious to spend passively consuming the endless opinions and highlights of total strangers online.",
        "A disciplined mind is a peaceful sanctuary amidst the digital age. I cultivate intentionality in every action, ensuring that my smartphone remains a helpful servant, never my master in life.",
        "Real accomplishments require uninterrupted hours of patient effort. I consciously resist the temptation of effortless entertainment so that I can produce work of genuine quality and lasting personal value today.",
        "I respect my intellect enough to feed it substance rather than trivia. By choosing presence over pixels, I honor my potential and nurture my own creative spirit with pure intention.",
        "The urge to scroll is merely a craving for novelty. By sitting with boredom and embracing stillness, I unlock the deep creativity and calm clarity that truly transform my life.",
        "I commit to being fully present for people around me, rather than dividing attention between conversations and glowing screens. True connection happens when I look up and listen with care.",
        "Each day gives me finite time and energy to build future goals. I will not squander this gift on endless feeds that leave me feeling scattered, tired, and unfulfilled today.",
        "Conscious restraint is the foundation of freedom and self-respect. I choose to say no to addictive digital rabbit holes so that I can say a wholehearted yes to living deeply.",
        "I release the compulsive habit of checking my phone every spare minute. In quiet spaces between tasks, I choose to breathe, reflect, and appreciate the richness of the present moment.",
        "By taking charge of my digital habits, I regain lost time. I dedicate these precious hours to learning, moving, creating, and living with authentic joyful purpose in the real world.",
        "I refuse to let synthetic notifications dictate the tempo of life. I decide when to engage, when to pause, and when to completely disconnect from the online world around me.",
        "True wisdom begins with understanding the difference between urgent noise and what is truly important. I filter out the trivial so that I can dedicate myself to what matters most.",
        "My mind deserves rest, clarity, and spaciousness rather than an unrelenting barrage. Today, I practice intentional digital minimalism and rediscover profound peace from quiet focused attention in every single task.",
        "I choose to live an active, courageous life rather than a passive bystander watching people live. I step away from the feed and step boldly into my own journey today.",
        "When I resist infinite feeds, I reclaim my ability to think deeply, read patiently, and reflect meaningfully. Deliberate discipline protects the very essence of my human intellect and daily focus.",
        "I am not obligated to witness every trend or opinion on the internet. I choose peace of mind over irrelevant information, protecting my emotional bandwidth for what truly counts today.",
        "Mastering my attention is the most important skill of the modern era. Today I choose to sharpen that skill by rejecting mindless consumption and embracing deep, purposeful mental engagement always.",
        "Every intentional pause before unlocking my phone reminds me I am in control. I choose focus over distraction, intention over impulse, and real-world living over virtual digital illusions right now.",
        "The satisfaction of finishing a meaningful project exceeds the thrill of scrolling through feeds. I choose lasting pride over momentary amusement, directing energy toward purposeful creative endeavors with steadfast discipline.",
        "I walk away from digital noise with a clear conscience and calm heart. My time is reclaimed, my focus is restored, and my life is lived with intentional presence today.",
    )

    val mantras50: List<String> = listOf(
        "I recognize that infinite scrolling feeds are carefully engineered to capture my attention for corporate profit, but my life belongs to me alone. I choose voluntary discipline over impulsive consumption, stepping away from this screen to pursue meaningful real-world accomplishments, cultivate deep personal relationships, and protect my sacred mental peace.",
        "True mastery over technology means using it as a deliberate instrument for my education and growth, rather than allowing restless impulses to dictate how I spend my time. Today, I establish healthy digital boundaries, breathe with conscious intention, and return to physical reality where my genuine purpose and creativity reside.",
        "The greatest achievements in history required unbroken hours of deep concentration and effort, qualities that mindless social media browsing relentlessly erodes. I refuse to trade my highest aspirations for superficial digital novelty, choosing instead to guard my focus fiercely and direct my undivided energy toward creating work of lasting value.",
        "When I silence the endless barrage of notifications, algorithmic recommendations, and curated digital highlights, I rediscover the quiet power of my own inner voice. I choose to live deeply in the present moment, experiencing life firsthand rather than passively observing a distorted virtual world through a small glowing glass display.",
        "Every minute spent lost in algorithmic feeds is a minute stolen from my health, my intellect, my passions, and the people I love. I commit to treating my attention with respect it deserves, replacing compulsive browsing with mindful presence, purposeful action, and the genuine satisfaction that comes from real achievements.",
        "Distraction is the silent thief of human potential, constantly tempting me to settle for fleeting dopamine instead of enduring fulfillment. By practicing conscious restraint and saying a resolute no to endless scrolling, I reclaim my time and build the unshakable mental discipline necessary to achieve all my most ambitious goals.",
        "I understand that the urge to mindlessly check social media is merely a reaction to boredom or discomfort, not an authentic need. I choose to meet this moment with calm awareness, breathing deeply through the impulse, and choosing purposeful constructive action over passive digital consumption that leaves my spirit exhausted.",
        "Living an intentional life means deciding beforehand what deserves my attention and refusing to let commercial algorithms make that choice for me. I enter this digital space with clear purpose, complete what is strictly necessary, and depart promptly so that I can invest my finite life energy into what matters.",
        "A calm, focused, and disciplined mind is the most valuable asset I can cultivate in an increasingly distracted world full of noisy demands. I deliberately protect my mental sanctuary by filtering out trivial digital clutter, honoring my intellect with deep thoughts, and grounding my daily awareness firmly in physical reality.",
        "The modern attention economy seeks to keep me perpetually dissatisfied and reactive, but I possess the power to walk away at any moment. Today, I embrace the quiet beauty of intentional living, choosing deep focus over surface-level entertainment, and finding genuine contentment in the simple reality of my everyday life.",
        "Real confidence comes from self-control and the knowledge that my actions align with my deepest personal values, not from superficial online engagement metrics. I release the need for digital validation, turning my gaze inward to cultivate genuine wisdom, resilient character, and the quiet strength that comes from disciplined daily habits.",
        "I refuse to let the continuous stream of internet chatter fragment my concentration and rob my creative voice. By setting strict boundaries with social media, I grant my mind spaciousness to think deeply, solve difficult problems, and produce work that makes a positive difference in the real world around me.",
        "Every conscious choice to put down my phone is a victory for my independence, my focus, and my personal freedom. I am not a passive spectator in my life; I am the author of my own days, and I choose to write them with presence, passion, and quiet dedicated effort.",
        "In a culture that glorifies constant connectivity and instant responses, I choose the quiet rebellion of stillness, patience, and deliberate unplugged living. My best ideas emerge when I allow my mind to wander freely without the intrusive stimulation of curated feeds, endless videos, and repetitive digital conversations that lead nowhere.",
        "I understand that life is finite and that the hours I spend staring at algorithmic screens will never be returned to me. Therefore, I choose to invest my precious time wisely, prioritizing physical health, intellectual curiosity, genuine community, and meaningful creative pursuits that will bring me lasting pride and happiness.",
        "Mindfulness is the practice of returning home to this very moment, finding wonder and peace in what is already here before me. I step out of virtual comparisons and digital envy, choosing instead to appreciate my unique journey and celebrate the everyday gifts of physical existence with a joyful heart.",
        "When I conquer the compulsion to constantly check feeds, I unlock an abundance of time and energy that transforms my daily productivity. I commit to showing up fully for my responsibilities, treating my work as a craft, and giving my absolute best effort without being derailed by petty digital distractions.",
        "The convenience of modern technology must never become an excuse for a fragmented mind and an undisciplined life. I hold the power to dictate the role of digital devices in my routine, ensuring they serve my goals while strictly preserving my capacity for deep, quiet, uninterrupted reflection and serious study.",
        "I choose to measure the quality of my day by the depth of my focus, the kindness of my interactions, and the progress of my goals. Sensational headlines and trivial updates have no power over my peace of mind when I deliberately anchor my attention in purposeful and disciplined habits.",
        "Inner strength is forged in the small, unseen moments when I decide to resist instant gratification in favor of long-term integrity. Choosing to close an addictive app and return to my real-world duties is an act of self-respect that elevates my character and builds lasting confidence in my own willpower.",
        "I recognize that behind every infinite feed lies an artificial system optimized to keep me watching, but my attention is not for sale. I choose to break free from passive consumerism, reclaiming my autonomy and investing my mental energy into learning, building, and contributing meaningfully to the world around me.",
        "A life well-lived is characterized by depth, purpose, and deliberate focus, not by the superficial breadth of content consumed on a screen. Today, I give my undivided attention to the task at hand, knowing that true excellence is born from the patient refusal to be distracted by trivial digital novelties.",
        "I let go of the fear of missing out, realizing that the only thing I truly risk missing is my own precious life passing by unlived. By stepping back from social media feeds, I create room for genuine presence, peaceful solitude, and deep restorative rest that revitalizes my mind completely.",
        "My intellect is a sacred garden that deserves to be tended with great care, not overwhelmed by endless streams of algorithmic digital weeds. I choose to nourish my curiosity with books, nature, meaningful conversations, and creative challenges that expand my perspective and strengthen my capacity for independent and critical thought.",
        "When I embrace the discipline of mindful browsing, I demonstrate respect for my time, my intelligence, and my future well-being. I refuse to let digital noise drain my mental vitality, choosing instead to direct my focused efforts toward building a life of purpose, honor, and true authentic real-world fulfillment today.",
        "The urge to scroll will pass if I simply pause, breathe, and observe it without judgment or compliance. In that brief space between stimulus and response lies my ultimate freedom to choose wisdom over impulse, peace over agitation, and intentional living over mindless digital habits that erode my personal potential.",
        "I remember that the curated online world presents an illusion of perfection that bears little resemblance to the rich complexity of reality. I choose to embrace my authentic life with all its challenges, finding deep joy in real effort, genuine connections, and the honest pursuit of personal growth and self-mastery.",
        "True digital wellness is not about deprivation, but about intentionality and alignment with what truly matters to my core values. I choose to engage with technology consciously, leaving behind addictive patterns and rediscovering the profound liberty of being fully awake, attentive, and engaged with the physical world around me today.",
        "I dedicate myself to doing fewer things with greater depth and excellence, refusing to let shallow online browsing dilute my creative power. By eliminating compulsive screen time, I make space for deep relationships, craft, physical vitality, and the quiet satisfaction of knowing that my attention was spent with deliberate purpose.",
        "Today I reclaim my attention, my time, and my peace of mind from the grip of mindless digital habits. I step forward into the world with clear eyes, a steady heart, and an unwavering commitment to living fully, deeply, and intentionally in the reality that truly matters most to me.",
    )

    val mantrasByWordCount: Map<Int, List<String>> = mapOf(
        10 to mantras10,
        15 to mantras15,
        20 to mantras20,
        30 to mantras30,
        50 to mantras50
    )

    /**
     * Retrieves the next random mantra for the specified word count, cycling through all
     * available sentences without duplicates until the full pool is exhausted.
     */
    @Synchronized
    fun getNextMantra(wordCount: Int): String {
        val list = mantrasByWordCount[wordCount] ?: mantras30
        var pool = unexhaustedPools[wordCount]
        if (pool.isNullOrEmpty()) {
            pool = list.indices.toMutableList()
            pool.shuffle()
            unexhaustedPools[wordCount] = pool
        }
        val chosenIndex = pool.removeAt(0)
        return list[chosenIndex]
    }

    /**
     * Resets the exhaustion tracking pool, forcing a reshuffle on the next request.
     */
    @Synchronized
    fun resetPool(wordCount: Int? = null) {
        if (wordCount != null) {
            unexhaustedPools.remove(wordCount)
        } else {
            unexhaustedPools.clear()
        }
    }
}
