import { For, Show } from "solid-js";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { formatDateTime } from "@/lib/format";
import type { MetaWordDetail, MetaWordInflectionDetail } from "@/types/api";

interface MetaWordDetailModalProps {
    isOpen: boolean;
    word: MetaWordDetail | null;
    loading: boolean;
    error: string;
    canGenerateWithAi: boolean;
    generating: boolean;
    onClose: () => void;
    onGenerateWithAi: () => void;
}

interface ReadonlyFieldProps {
    id: string;
    label: string;
    value?: string | number | null;
}

const displayValue = (value?: string | number | null) => value == null || value === "" ? "未填写" : String(value);

function ReadonlyField(props: ReadonlyFieldProps) {
    return (
        <div class="space-y-2">
            <Label for={props.id}>{props.label}</Label>
            <Input
                id={props.id}
                class="bg-muted/25 text-foreground"
                readOnly
                value={displayValue(props.value)}
            />
        </div>
    );
}

function ReadonlyTextArea(props: ReadonlyFieldProps & { rows?: number }) {
    return (
        <div class="space-y-2">
            <Label for={props.id}>{props.label}</Label>
            <Textarea
                id={props.id}
                class="resize-none bg-muted/25 text-foreground"
                readOnly
                rows={props.rows ?? 3}
                value={displayValue(props.value)}
            />
        </div>
    );
}

const hasInflection = (inflection?: MetaWordInflectionDetail | null) => Boolean(
    inflection && Object.values(inflection).some((value) => value != null && value !== ""),
);

export function MetaWordDetailModal(props: MetaWordDetailModalProps) {
    return (
        <Show when={props.isOpen}>
            <div
                class="fixed inset-0 z-50 flex items-start justify-center bg-slate-950/45 p-4 pt-[40px] backdrop-blur-sm"
                onClick={props.onClose}
            >
                <section
                    aria-labelledby="meta-word-detail-dialog-title"
                    aria-modal="true"
                    class="max-h-[calc(100vh-80px)] w-full max-w-5xl overflow-y-auto rounded-[28px] border border-border/70 bg-background shadow-2xl"
                    role="dialog"
                    onClick={(event) => event.stopPropagation()}
                >
                    <header class="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-border/60 bg-background/95 px-6 py-5 backdrop-blur">
                        <div>
                            <p class="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Meta word</p>
                            <h2 class="mt-1 font-display text-2xl font-semibold tracking-tight" id="meta-word-detail-dialog-title">
                                {props.word?.word || "单词详细数据"}
                            </h2>
                            <p class="mt-1 text-sm text-muted-foreground">按数据库的结构化词条层级展示；可用 AI 自动补全后立即刷新。</p>
                        </div>
                        <div class="flex shrink-0 flex-wrap justify-end gap-2">
                            <Show when={props.canGenerateWithAi && props.word}>
                                <Button
                                    disabled={props.loading || props.generating}
                                    size="sm"
                                    type="button"
                                    onClick={props.onGenerateWithAi}
                                >
                                    {props.generating ? "AI 补全中..." : "AI 自动补全"}
                                </Button>
                            </Show>
                            <Button aria-label="关闭单词详情" size="sm" type="button" variant="outline" onClick={props.onClose}>
                                关闭
                            </Button>
                        </div>
                    </header>

                    <div class="space-y-6 p-6">
                        <Show when={props.loading}>
                            <div class="rounded-2xl border border-border/70 bg-muted/20 p-6 text-sm text-muted-foreground">
                                正在加载单词详细数据...
                            </div>
                        </Show>
                        <Show when={props.error}>
                            <Alert class="border-destructive/30 bg-destructive/10 text-destructive">{props.error}</Alert>
                        </Show>

                        <Show when={props.word}>
                            {(word) => (
                                <>
                                    <section class="rounded-2xl border border-border/70 bg-background/70 p-5">
                                        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
                                            <div>
                                                <h3 class="text-base font-semibold text-foreground">基础信息</h3>
                                                <p class="mt-1 text-sm text-muted-foreground">词条身份、规范拼写和学习难度。</p>
                                            </div>
                                            <Badge variant="outline">ID {word().id}</Badge>
                                        </div>
                                        <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                                            <ReadonlyField id="meta-word-word" label="单词" value={word().word} />
                                            <ReadonlyField id="meta-word-normalized" label="规范化单词" value={word().normalizedWord} />
                                            <ReadonlyField id="meta-word-difficulty" label="难度等级" value={word().difficulty} />
                                            <ReadonlyField id="meta-word-flat-pos" label="列表词性" value={word().partOfSpeech} />
                                        </div>
                                    </section>

                                    <section class="rounded-2xl border border-border/70 bg-background/70 p-5">
                                        <div class="mb-4">
                                            <h3 class="text-base font-semibold text-foreground">发音与音节</h3>
                                            <p class="mt-1 text-sm text-muted-foreground">英式、美式音标以及按拼写顺序拆分的音节。</p>
                                        </div>
                                        <div class="grid gap-4 md:grid-cols-3">
                                            <ReadonlyField id="meta-word-flat-phonetic" label="列表音标" value={word().phonetic} />
                                            <ReadonlyField id="meta-word-uk-phonetic" label="英式音标" value={word().phoneticDetail?.uk} />
                                            <ReadonlyField id="meta-word-us-phonetic" label="美式音标" value={word().phoneticDetail?.us} />
                                        </div>

                                        <div class="mt-5 space-y-3">
                                            <div class="flex items-center justify-between gap-3">
                                                <h4 class="text-sm font-semibold text-foreground">音节拆分</h4>
                                                <Badge variant="secondary">{word().syllableDetail?.segments?.length ?? 0} 段</Badge>
                                            </div>
                                            <Show
                                                when={(word().syllableDetail?.segments?.length ?? 0) > 0}
                                                fallback={<p class="rounded-xl bg-muted/25 px-4 py-3 text-sm text-muted-foreground">暂无音节拆分数据</p>}
                                            >
                                                <For each={word().syllableDetail?.segments ?? []}>
                                                    {(segment, segmentIndex) => (
                                                        <div class="grid gap-3 rounded-xl border border-border/60 bg-muted/15 p-4 md:grid-cols-3">
                                                            <ReadonlyField id={`syllable-${segmentIndex()}-text`} label={`第 ${segmentIndex() + 1} 段拼写`} value={segment.text} />
                                                            <ReadonlyField id={`syllable-${segmentIndex()}-uk`} label="英式音节音标" value={segment.ukPhonetic} />
                                                            <ReadonlyField id={`syllable-${segmentIndex()}-us`} label="美式音节音标" value={segment.usPhonetic} />
                                                            <div class="md:col-span-3 grid gap-3 md:grid-cols-2">
                                                                <ReadonlyField id={`syllable-${segmentIndex()}-uk-audio`} label="英式音频地址" value={segment.ukAudioUrl} />
                                                                <ReadonlyField id={`syllable-${segmentIndex()}-us-audio`} label="美式音频地址" value={segment.usAudioUrl} />
                                                            </div>
                                                        </div>
                                                    )}
                                                </For>
                                            </Show>
                                        </div>
                                    </section>

                                    <section class="rounded-2xl border border-border/70 bg-background/70 p-5">
                                        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
                                            <div>
                                                <h3 class="text-base font-semibold text-foreground">词性与释义</h3>
                                                <p class="mt-1 text-sm text-muted-foreground">每个词性独立承载释义、例句、词形变化和同反义词。</p>
                                            </div>
                                            <Badge>{word().partOfSpeechDetail?.length ?? 0} 个词性</Badge>
                                        </div>

                                        <Show
                                            when={(word().partOfSpeechDetail?.length ?? 0) > 0}
                                            fallback={<p class="rounded-xl bg-muted/25 px-4 py-3 text-sm text-muted-foreground">暂无结构化词性和释义数据</p>}
                                        >
                                            <div class="space-y-5">
                                                <For each={word().partOfSpeechDetail ?? []}>
                                                    {(partOfSpeech, posIndex) => (
                                                        <article class="rounded-2xl border border-primary/15 bg-primary/[0.025] p-5">
                                                            <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
                                                                <div class="flex items-center gap-2">
                                                                    <Badge variant="default">词性 {posIndex() + 1}</Badge>
                                                                    <span class="text-lg font-semibold text-foreground">{displayValue(partOfSpeech.pos)}</span>
                                                                </div>
                                                                <span class="text-sm text-muted-foreground">{partOfSpeech.definitions?.length ?? 0} 条释义</span>
                                                            </div>

                                                            <ReadonlyField id={`pos-${posIndex()}-name`} label="词性缩写" value={partOfSpeech.pos} />

                                                            <div class="mt-5 space-y-4">
                                                                <For each={partOfSpeech.definitions ?? []}>
                                                                    {(definition, definitionIndex) => (
                                                                        <section class="rounded-xl border border-border/60 bg-background p-4">
                                                                            <h5 class="mb-3 text-sm font-semibold text-foreground">释义 {definitionIndex() + 1}</h5>
                                                                            <div class="grid gap-4 md:grid-cols-2">
                                                                                <ReadonlyTextArea id={`pos-${posIndex()}-definition-${definitionIndex()}-en`} label="英文释义" value={definition.definition} />
                                                                                <ReadonlyTextArea id={`pos-${posIndex()}-definition-${definitionIndex()}-zh`} label="中文释义" value={definition.translation} />
                                                                            </div>

                                                                            <div class="mt-4 space-y-3">
                                                                                <div class="flex items-center justify-between gap-3">
                                                                                    <h6 class="text-sm font-medium text-foreground">例句</h6>
                                                                                    <span class="text-xs text-muted-foreground">{definition.exampleSentences?.length ?? 0} 条</span>
                                                                                </div>
                                                                                <Show
                                                                                    when={(definition.exampleSentences?.length ?? 0) > 0}
                                                                                    fallback={<p class="rounded-lg bg-muted/25 px-3 py-2 text-sm text-muted-foreground">暂无例句</p>}
                                                                                >
                                                                                    <For each={definition.exampleSentences ?? []}>
                                                                                        {(example, exampleIndex) => (
                                                                                            <div class="grid gap-3 rounded-lg bg-muted/20 p-3 md:grid-cols-2">
                                                                                                <ReadonlyTextArea id={`pos-${posIndex()}-definition-${definitionIndex()}-example-${exampleIndex()}-en`} label={`英文例句 ${exampleIndex() + 1}`} rows={2} value={example.sentence} />
                                                                                                <ReadonlyTextArea id={`pos-${posIndex()}-definition-${definitionIndex()}-example-${exampleIndex()}-zh`} label="例句翻译" rows={2} value={example.translation} />
                                                                                            </div>
                                                                                        )}
                                                                                    </For>
                                                                                </Show>
                                                                            </div>
                                                                        </section>
                                                                    )}
                                                                </For>
                                                            </div>

                                                            <Show when={hasInflection(partOfSpeech.inflection)}>
                                                                <div class="mt-5 rounded-xl border border-border/60 bg-background p-4">
                                                                    <h5 class="mb-3 text-sm font-semibold text-foreground">词形变化</h5>
                                                                    <div class="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
                                                                        <ReadonlyField id={`pos-${posIndex()}-plural`} label="复数" value={partOfSpeech.inflection?.plural} />
                                                                        <ReadonlyField id={`pos-${posIndex()}-past`} label="过去式" value={partOfSpeech.inflection?.past} />
                                                                        <ReadonlyField id={`pos-${posIndex()}-past-participle`} label="过去分词" value={partOfSpeech.inflection?.pastParticiple} />
                                                                        <ReadonlyField id={`pos-${posIndex()}-present-participle`} label="现在分词" value={partOfSpeech.inflection?.presentParticiple} />
                                                                        <ReadonlyField id={`pos-${posIndex()}-third-person`} label="第三人称单数" value={partOfSpeech.inflection?.thirdPersonSingular} />
                                                                        <ReadonlyField id={`pos-${posIndex()}-comparative`} label="比较级" value={partOfSpeech.inflection?.comparative} />
                                                                        <ReadonlyField id={`pos-${posIndex()}-superlative`} label="最高级" value={partOfSpeech.inflection?.superlative} />
                                                                    </div>
                                                                </div>
                                                            </Show>

                                                            <div class="mt-5 grid gap-4 md:grid-cols-2">
                                                                <ReadonlyTextArea id={`pos-${posIndex()}-synonyms`} label="近义词" rows={2} value={partOfSpeech.synonyms?.join("、")} />
                                                                <ReadonlyTextArea id={`pos-${posIndex()}-antonyms`} label="反义词" rows={2} value={partOfSpeech.antonyms?.join("、")} />
                                                            </div>
                                                        </article>
                                                    )}
                                                </For>
                                            </div>
                                        </Show>
                                    </section>

                                    <details class="rounded-2xl border border-border/70 bg-muted/10 p-5">
                                        <summary class="cursor-pointer text-sm font-semibold text-foreground">列表兼容字段与记录时间</summary>
                                        <p class="mt-2 text-sm text-muted-foreground">这些平铺字段用于旧接口和列表检索，结构化词性与释义才是完整数据。</p>
                                        <div class="mt-4 grid gap-4 md:grid-cols-2">
                                            <ReadonlyTextArea id="meta-word-flat-definition" label="平铺英文释义" value={word().definition} />
                                            <ReadonlyTextArea id="meta-word-flat-translation" label="平铺中文释义" value={word().translation} />
                                            <div class="md:col-span-2">
                                                <ReadonlyTextArea id="meta-word-flat-example" label="平铺例句" value={word().exampleSentence} />
                                            </div>
                                            <ReadonlyField id="meta-word-created-at" label="创建时间" value={formatDateTime(word().createdAt)} />
                                            <ReadonlyField id="meta-word-updated-at" label="更新时间" value={formatDateTime(word().updatedAt)} />
                                        </div>
                                    </details>
                                </>
                            )}
                        </Show>
                    </div>
                </section>
            </div>
        </Show>
    );
}
