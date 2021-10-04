
import handlers.Context;
import handlers.MessageHandler;
import handlers.State;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class DialogMaker {
    private static DictionaryRepositoryByTopics dictionary;

    static {
        try {
            dictionary = new DictionaryRepositoryByTopics();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<Integer, State> MakeDialog(Context context) throws IOException {
        var states = new HashMap<Integer, State>();
        states.put(1,
                MakeState(
                        new HashMap<>(Map.of(
                                "статистика", DialogMaker::printStatistic,
                                "словарь", DialogMaker::printLearnedWords,
                                "повторить", DialogMaker::startTest,
                                "выучить", DialogMaker::learnWords)),
                        null));
        states.put(2,
                MakeState(new HashMap<>(Map.of("назад", DialogMaker::back)),
                        null));
        states.put(3,
                MakeState(new HashMap<>(Map.of("назад", DialogMaker::back)),
                        null));
        states.put(4,
                MakeState(new HashMap<>(), DialogMaker::askTopic));
        states.put(5,
                MakeState(new HashMap<>(), DialogMaker::checkWord));
        states.put(6,
                MakeState(
                        new HashMap<>(Map.of(
                                "заново", DialogMaker::startTest,
                                "назад", DialogMaker::back)),
                        null));
        states.put(7,
                MakeState(new HashMap<>(), DialogMaker::printWordsToLearn));
        states.put(8,
                MakeState(
                        new HashMap<>(Map.of(
                                "заново", DialogMaker::learnWords,
                                "тест", DialogMaker::startTest,
                                "назад", DialogMaker::back)),
                        null));
        return states;
    }

    private static State MakeState(HashMap<String, Function<Context, Integer>> actions, Function<Context, Integer> fallback) {
        var handlers = new ArrayList<MessageHandler>();
        actions.forEach((key, value) -> handlers.add(new MessageHandler(key, value)));
        return new State(handlers, fallback);
    }

    private static Integer printStatistic(Context context) {
        var result = 0;
        for(Map.Entry<String, LearnedWords> e : ((HashMap<String, LearnedWords>) context.get("learnedWords")).entrySet())
        {
            result += e.getValue().WellLearnedWords.size();
        }
        System.out.println("Выучено слов: " + result);
        return 2;
    }

    private static Integer startTest(Context context) {
        context.set("correctAnswers", 0);
        context.set("attempts", 0);
        System.out.println("Выбери тему");
        return 4;
    }

    private static Integer printLearnedWords(Context context) {
        System.out.println("Твои выученные слова:");
        for(Map.Entry<String, LearnedWords> e : ((HashMap<String, LearnedWords>) context.get("learnedWords")).entrySet())
        {
            for(var i = 0; i < e.getValue().WellLearnedWords.size(); i++)
            {
                System.out.println(e.getValue().WellLearnedWords.get(i).getWord());
            }
        }
        return 3;
    }


    private static Integer askWord(Context context) {
        System.out.println(((ArrayList<QuestionForm>)context.get("questions")).get((Integer)context.get("correctAnswers")).question);
        return 5;
    }

    private static Integer askTopic(Context context) {
        var message = context.getMessage().substring(0, 1).toUpperCase(Locale.ROOT) + context.getMessage().substring(1);
        if(!((HashMap<String, LearnedWords>) context.get("learnedWords")).containsKey(message)) {
            System.out.println("Нет такой темы");
            return 6;
        }
        if(Adapter.class.cast(context.get("adapter")).GetUserQuestions(context.getMessage(), (HashMap<String, LearnedWords>) context.get("learnedWords"), 1) == null)
        {
            System.out.println("Ты еще не учил слова");
            return 1;
        }
        context.set("topic", message);
        context.set("questions", Adapter.class.cast(context.get("adapter")).GetUserQuestions(context.getMessage(), (HashMap<String, LearnedWords>) context.get("learnedWords"), 1));
        return askWord(context);
    }

    private static WordAndTranslate GetWordByQuestion(QuestionForm question, Integer index, Integer maxQuestions){
        if(index < maxQuestions){
            return new WordAndTranslate(question.question, question.answer);
        }
        return new WordAndTranslate(question.answer, question.question);
    }


    private static Integer checkWord(Context context) {
        int maxQuestions = 1;
        var learned = ((HashMap<String, LearnedWords>)context.get("learnedWords"));
        var question = ((ArrayList<QuestionForm>)context.get("questions")).get((int)context.get("correctAnswers"));
        if((int)context.get("correctAnswers") == 2 * maxQuestions - 1)
        {
            if(context.getMessage().equals(question.answer))
            {
                if((Integer)context.get("attempts") == 0 && learned.get((String) context.get("topic")).BadlyLearnedWords.contains(GetWordByQuestion(question, 2 * maxQuestions - 1, maxQuestions)))
                {
                    learned.get((String) context.get("topic")).BadlyLearnedWords.remove(GetWordByQuestion(question, 2 * maxQuestions - 1, maxQuestions));
                    learned.get((String) context.get("topic")).NormallyLearnedWords.add(GetWordByQuestion(question, 2 * maxQuestions - 1, maxQuestions));
                }
                else if((Integer)context.get("attempts") == 0 && learned.get((String) context.get("topic")).NormallyLearnedWords.contains(GetWordByQuestion(question, 2 * maxQuestions - 1, maxQuestions)))
                {
                    learned.get((String) context.get("topic")).NormallyLearnedWords.remove(GetWordByQuestion(question, 2 * maxQuestions - 1, maxQuestions));
                    learned.get((String) context.get("topic")).WellLearnedWords.add(GetWordByQuestion(question, 2 * maxQuestions - 1, maxQuestions));
                }
                context.set("attempts", 0);
                System.out.println("Правильно");
                System.out.println("Закончено");
                return 6;
            }
            context.set("attempts", 0);
            question.UpdateHint();
            System.out.println(question.hint);
            return 5;
        }
        else if(context.getMessage().equals(question.answer))
        {
            var index = (int)context.get("correctAnswers");
            var w = GetWordByQuestion(question, index, maxQuestions);
            if((Integer)context.get("attempts") == 0 && learned.get((String) context.get("topic")).BadlyLearnedWords.contains(GetWordByQuestion(question, index, maxQuestions)))
            {
                learned.get((String) context.get("topic")).BadlyLearnedWords.remove(GetWordByQuestion(question, index, maxQuestions));
                learned.get((String) context.get("topic")).NormallyLearnedWords.add(GetWordByQuestion(question, index, maxQuestions));
            }
            else if((Integer)context.get("attempts") == 0 && learned.get((String) context.get("topic")).NormallyLearnedWords.contains(GetWordByQuestion(question, index, maxQuestions)))
            {
                learned.get((String) context.get("topic")).NormallyLearnedWords.remove(GetWordByQuestion(question, index, maxQuestions));
                learned.get((String) context.get("topic")).WellLearnedWords.add(GetWordByQuestion(question, index, maxQuestions));
            }
            context.set("correctAnswers", (int)context.get("correctAnswers") + 1);
            System.out.println("Правильно");
            context.set("attempts", 0);
            return askWord(context);
        }
        else
        {
            var index = (int)context.get("correctAnswers");
            if(learned.get((String) context.get("topic")).WellLearnedWords.contains(GetWordByQuestion(question, index, maxQuestions)))
            {
                learned.get((String) context.get("topic")).WellLearnedWords.remove(GetWordByQuestion(question, index, maxQuestions));
                learned.get((String) context.get("topic")).NormallyLearnedWords.add(GetWordByQuestion(question, index, maxQuestions));
            }
            else if(learned.get((String) context.get("topic")).NormallyLearnedWords.contains(GetWordByQuestion(question, index, maxQuestions)))
            {
                learned.get((String) context.get("topic")).NormallyLearnedWords.remove(GetWordByQuestion(question, index, maxQuestions));
                learned.get((String) context.get("topic")).BadlyLearnedWords.add(GetWordByQuestion(question, index, maxQuestions));
            }
            context.set("attempts", 1);
            System.out.println("Неправильно");
            question.UpdateHint();
            System.out.println(question.hint);
        }
        return 5;
    }



    private static Integer back(Context context) {
        return 1;
    }

    private static Integer learnWords(Context context) {
        System.out.println("Напиши интересующую тематику");
        return 7;
    }

    private static Integer printWordsToLearn(Context context) {
        var message = context.getMessage().substring(0, 1).toUpperCase(Locale.ROOT) + context.getMessage().substring(1);
        if(!((HashMap<String, LearnedWords>)context.get("learnedWords")).containsKey(message))
        {
            System.out.println("Нет такой темы");
            return 7;
        }
        System.out.println("Вывожу слова по теме: " + context.getMessage());
        var newWords = 0;
        for(var i = 0; i < dictionary.DictionaryByTopics.get(message).size() && newWords < 2; i++){
            var word = dictionary.DictionaryByTopics.get(message).get(i);
            if(!((HashMap<String, LearnedWords>)context.get("learnedWords")).get(message).WellLearnedWords.contains(word) &&
                    !((HashMap<String, LearnedWords>)context.get("learnedWords")).get(message).NormallyLearnedWords.contains(word) &&
                    !((HashMap<String, LearnedWords>)context.get("learnedWords")).get(message).BadlyLearnedWords.contains(word))
            {
                ((HashMap<String, LearnedWords>)context.get("learnedWords")).get(message).BadlyLearnedWords.add(word);
                System.out.println(word.getWord() + " - " + word.getTranslate());
                newWords++;
            }

        }
        return 8;
    }
}

