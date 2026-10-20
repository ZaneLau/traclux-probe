package com.geoprobe.service.engine.v42;

import java.util.ArrayList;
import java.util.List;

public final class LinguisticDistortionRules {

    private LinguisticDistortionRules() {
    }

    public interface PromptInstructionRule {

        String toPromptInstruction();
    }

    public enum IllocutionaryForce implements PromptInstructionRule {
        DIRECTIVE_DOMINANT("【语用规则】必须明显更直给、少铺垫、少寒暄，像急着要结果；优先用“直接说 / 就告诉我 / 帮我定一个”这类结果导向问法，但仍要像真人会说的话"),
        EXPRESSIVE_DOMINANT("【语用规则】句首必须先用第一人称的情绪或抱怨短分句起势（如“我都看烦了 / 越挑越乱 / 选得有点崩溃”），让情绪先占前景，再把真正的问题放到后半句；不要只在句首轻轻点一下情绪，然后立刻退回成纯信息查询"),
        REPRESENTATIVE_ASSERTIVE("【语用规则】你心里已经有偏向，用确认式问法，而不是完全开放式问法"),
        VERDICTIVE_DECLARATIVE("【语用规则】带一点挑剔和质疑感，但不要写成评论区差评文案");

        private final String promptInstruction;

        IllocutionaryForce(String promptInstruction) {
            this.promptInstruction = promptInstruction;
        }

        @Override
        public String toPromptInstruction() {
            return promptInstruction;
        }
    }

    public enum RegisterVariation implements PromptInstructionRule {
        INVOLVED_PERSUASION("【语域规则】可以主观一点、个人一点，但不要变成口号式堆词"),
        ACADEMIC_HEDGING("【语域规则】保留一点犹豫和不确定感，像真人拿不准时会说的话"),
        INFORMATIONAL_ELABORATION("【语域规则】表达更具体、更讲条件，但仍要像人在聊天，不像需求文档");

        private final String promptInstruction;

        RegisterVariation(String promptInstruction) {
            this.promptInstruction = promptInstruction;
        }

        @Override
        public String toPromptInstruction() {
            return promptInstruction;
        }
    }

    public enum CognitiveDistortion implements PromptInstructionRule {
        ALL_OR_NOTHING("【认知规则】可以更绝对化一点，但别把句子写成口号"),
        CATASTROPHIZING("【认知规则】句子前半段必须先露出“买错会带来个人后果”的担忧，而不只是普通求稳；优先写成“买错会后悔 / 折腾 / 很麻烦 / 怕翻车”这类落到自己身上的结果，再顺着提出问题。不要只写“省心 / 不踩雷 / 怕买错”这种过轻的泛化说法"),
        MIND_READING("【认知规则】可以带一点猜疑或道听途说，但不要堆满阴谋论措辞"),
        OVERGENERALIZATION("【认知规则】可以一杆子打翻一船人，但句子仍要自然可读");

        private final String promptInstruction;

        CognitiveDistortion(String promptInstruction) {
            this.promptInstruction = promptInstruction;
        }

        @Override
        public String toPromptInstruction() {
            return promptInstruction;
        }
    }

    public enum SyntacticOffloading implements PromptInstructionRule {
        HEURISTIC_KEYWORD_AGG("【句法规则】允许更短、更像搜索式提问，但仍必须读起来像一句自然问题"),
        FRAGMENTED_IMPERATIVES("【句法规则】允许偏短、偏急、带口语碎片，但不能变成标签堆叠"),
        DEICTIC_AMBIGUITY("【句法规则】允许省略部分背景，用“这个/那个”一类指代，但不能让人完全看不懂");

        private final String promptInstruction;

        SyntacticOffloading(String promptInstruction) {
            this.promptInstruction = promptInstruction;
        }

        @Override
        public String toPromptInstruction() {
            return promptInstruction;
        }
    }

    public enum PragmaticIndirectness implements PromptInstructionRule {
        ABILITY_CONDITIONED("【间接规则】可以把请求藏在“能不能/有没有办法”这种问法里"),
        STATE_CONDITIONED("【间接规则】开头先交代一个处境或麻烦，再顺着提出问题；不要一上来直接问“哪个好 / 怎么选”，也不要只剩困境没有问题"),
        POLITENESS_MASKED("【间接规则】可以客气一点，但不要写成客服套话");

        private final String promptInstruction;

        PragmaticIndirectness(String promptInstruction) {
            this.promptInstruction = promptInstruction;
        }

        @Override
        public String toPromptInstruction() {
            return promptInstruction;
        }
    }

    public static List<PromptInstructionRule> allRules() {
        List<PromptInstructionRule> rules = new ArrayList<>(17);
        rules.addAll(List.of(IllocutionaryForce.values()));
        rules.addAll(List.of(RegisterVariation.values()));
        rules.addAll(List.of(CognitiveDistortion.values()));
        rules.addAll(List.of(SyntacticOffloading.values()));
        rules.addAll(List.of(PragmaticIndirectness.values()));
        return List.copyOf(rules);
    }
}
