import {useContext, useState} from "react";
import {
    addWordSequence,
    allowEdit,
    deleteLink,
    deleteRule,
    fetchPathsForAllGraphs,
    traceRule
} from "@/api";
import WordLink from "@/components/WordLink";
import {fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import RuleForm from "@/forms/RuleForm";
import SourceRefs from "@/components/SourceRefs";
import RichText from "@/components/RichText";
import RuleLinkForm from "@/forms/RuleLinkForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import {GraphContext} from "@/components/Contexts";
import WordGloss from "@/components/WordGloss";
import RuleLink from "@/components/RuleLink";
import {RuleExampleViewModel, RuleTraceResult, RuleViewModel} from "@/models";
import LanguageSelect from "@/components/LanguageSelect";
import {Form} from "react-hook-form";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rule/${context.params.id}`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("rules", (r) => ({id: r.id.toString()}))
}

function ExampleList(params: {rule: RuleViewModel, examples: RuleExampleViewModel[]}) {
    const {rule, examples} = params
    const graph = useContext(GraphContext)
    const haveSteps = examples.some(ex => ex.allRules.length > 1)
    const haveExpected = examples.some(ex => ex.expectedWord !== null && ex.expectedWord !== ex.fromWord.text)
    return <table className="tableWithBorders">
        <thead>
        <tr>
            <th>From</th>
            <th>To</th>
            <th>Gloss</th>
            {haveSteps && <th>Steps</th>}
            {haveExpected && <th>Expected</th>}
        </tr>
        </thead>
        <tbody>
        {params.examples.map(ex => <tr key={ex.toWord.id}>
            <td>
                <WordLink word={ex.toWord} baseLanguage={rule.toLang}/>
            </td>
            <td>
                <WordLink word={ex.fromWord} baseLanguage={rule.toLang}/>
            </td>
            <td>
                <WordGloss gloss={ex.toWord.gloss}/>
            </td>
            {haveSteps && <td>
                {ex.allRules.length > 1 && ex.ruleResults.length === 0 && <>
                    {ex.allRules.map((rl, i) => <>
                        {i > 0 && ", "}
                        {rl.toRuleId !== rule.id && <Link href={`/${graph}/rule/${rl.toRuleId}`}>{rl.toRuleName}</Link>}
                        {rl.toRuleId === rule.id && rl.toRuleName}
                    </>)}
                </>}
                {ex.allRules.length > 1 && ex.ruleResults.length > 0 && <>
                    {ex.toWord.text}
                    {ex.allRules.map((rl, i) => <>
                        {' '}<Link href={`/${graph}/rule/${rl.toRuleId}`} title={rl.toRuleName}>&gt;</Link>{' '}
                        {rl.toRuleId === rule.id && <b>{ex.ruleResults[i]}</b>}
                        {rl.toRuleId !== rule.id && ex.ruleResults[i]}
                    </>)}
                </>}
            </td>}
            {haveExpected && <td>
                {ex.expectedWord !== null && ex.expectedWord !== ex.fromWord.text && ex.expectedWord}
            </td>}
        </tr>)}
        </tbody>
    </table>
}

interface TraceFormData {
    traceLanguage?: string
    traceWord?: string
}

export default function Rule(params) {
    const rule = params.loaderData as RuleViewModel
    const [editMode, setEditMode] = useState(false)
    const [linkMode, setLinkMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    const [lastExampleSource, setLastExampleSource] = useState("")
    const [showExampleForm, setShowExampleForm] = useState(false)
    const [showTraceForm, setShowTraceForm] = useState(false)
    const [traceWord, setTraceWord] = useState("")
    const [traceLanguage, setTraceLanguage] = useState(rule.toLang)
    const [traceReverse, setTraceReverse] = useState(false)
    const [traceResult, setTraceResult] = useState("")
    const [exampleUnmatched, setExampleUnmatched] = useState([])
    const [focusTarget, setFocusTarget] = useState(null)
    const router = useRouter()
    const graph = router.query.graph as string

    function linkSubmitted() {
        setLinkMode(false)
        router.replace(router.asPath)
    }

    function submitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    function deleteRuleClicked() {
        if (window.confirm("Delete this rule?")) {
            deleteRule(graph, rule.id)
                .then(() => router.push(`/${graph}/rules/${rule.toLang}`))
        }
    }

    async function deleteLinkClicked(entityId: number, linkType: string) {
        if (window.confirm("Delete this link?")) {
            const r = await deleteLink(graph, entityId, rule.id, linkType)
            if (r.status === 200) {
                router.replace(router.asPath)
            } else {
                r.json().then(r => setErrorText(r.message))
            }
        }
    }

    function createExample(data) {
        return addWordSequence(graph, data.exampleText, data.exampleSource)
    }

    async function exampleSubmitted(r, data) {
        setShowExampleForm(false)
        setLastExampleSource(data.exampleSource)
        if (r.ruleIds.indexOf(rule.id) >= 0) {
            setErrorText("")
            setExampleUnmatched([])
            router.replace(router.asPath)
        } else {
            setErrorText("Example does not match rule")
            setExampleUnmatched(r.words)
        }
    }

    function prepareTrace(reverse) {
        setShowTraceForm(true)
        setTraceReverse(reverse)
    }

    async function runTrace(data: TraceFormData): Promise<Response> {
        return traceRule(graph, rule.id, data.traceWord, traceReverse, data.traceLanguage)
    }

    function showTrace(result: RuleTraceResult) {
        setTraceResult(result.trace)
    }

    return <>
    <Breadcrumbs langId={rule.toLang} langName={rule.toLangFullName}
                     steps={[{title: "Rules", url: `/${graph}/rules/${rule.toLang}`}]}
                     title={rule.name}/>
        {rule.fromLang !== rule.toLang && <p>From {rule.fromLangFullName} to {rule.toLangFullName}</p>}
        {rule.paradigmId !== null && <p>Paradigm: <Link href={`/${graph}/paradigm/${rule.paradigmId}`}>{rule.paradigmName}</Link></p>}
        {!editMode && <>
            {rule.fromPOS.length > 0 && <p>From POS: {rule.fromPOS.join(", ")}</p>}
            {rule.toPOS && <p>To POS: {rule.toPOS}</p>}
            {rule.addedCategories && <p>Added category values: <span className="glossAbbreviation">{rule.addedCategories.toLowerCase()}</span> ({rule.addedCategoryDisplayNames})</p>}
            {rule.replacedCategories && <p>Replaced category values: <span className="glossAbbreviation">{rule.replacedCategories.toLowerCase()}</span></p>}
            <SourceRefs source={rule.source}/>
            <p/>
            {rule.paradigmPreRule != null && <p>Paradigm pre rule: <RuleLink rule={rule.paradigmPreRule}/></p>}
            <ul>
                {rule.preInstructions.map(r => <li><RichText richText={r}></RichText></li>)}
            </ul>
            {rule.branches.map(b => <>
                {b.comment != null && <div className="ruleComment">{b.comment}</div> }
                {b.conditions.fragments.length > 0 && <div><RichText richText={b.conditions}/>:</div>}
                <ul>
                    {b.instructions.map(i => <li><RichText richText={i}></RichText></li>)}
                </ul>
                {b.examples.length > 0 && <>
                    <h3>Examples</h3>
                    <ExampleList rule={rule} examples={b.examples}/>
                    <p/>
                </>}
            </>)}
            {rule.postInstructions.length > 0 && <>
                <h3>Post instructions</h3>
                <ul>
                    {rule.postInstructions.map(r => <li><RichText richText={r}></RichText></li>)}
                </ul>
            </>}
            {rule.paradigmPostRule != null && <p>Paradigm post rule: <RuleLink rule={rule.paradigmPostRule}/></p>}
            {rule.notes != null && <>
                <h3>Notes</h3>
                <p>{rule.notes}</p>
            </>}
            {rule.orphanExamples.length > 0 && <>
                <h3>Orphan Examples</h3>
                <ExampleList rule={rule} examples={rule.orphanExamples}/>
                <p/>
            </>}
        </>}
        {editMode && <RuleForm
            updateId={rule.id}
            initialType={rule.phonemic ? "phono" : "morpho"}
            defaultValues={{
                name: rule.name,
                fromLang: rule.fromLang,
                toLang: rule.toLang,
                addedCategories: rule.addedCategories,
                replacedCategories: rule.replacedCategories,
                fromPOS: rule.fromPOS.join(","),
                toPOS: rule.toPOS,
                source: rule.sourceEditableText,
                notes: rule.notes,
                text: rule.editableText
            }}
            submitted={submitted}
            cancelled={() => setEditMode(false)}
        />}

        {allowEdit() && <>
            {!editMode && <><button onClick={() => setEditMode(true)}>Edit</button>{' '}</>}
            {!linkMode && <><button onClick={() => setLinkMode(true)}>Add Link</button>{' '}</>}
            <button onClick={() => deleteRuleClicked()}>{"Delete"}</button>
        </>}
        {rule.links.length > 0 && <>
            <h3>Related rules</h3>
            {rule.links.map(rl => <>
                <Link href={`/${graph}/rule/${rl.toRuleId}`}>{rl.toRuleName}</Link>
                {rl.notes && <> &ndash; {rl.notes}</>}
                <SourceRefs source={rl.source} span={true}/>
                {allowEdit() && <>
                    &nbsp;(<span className="inlineButtonLink">
                            <button className="inlineButton"
                                    onClick={() => deleteLinkClicked(rl.toRuleId, rl.linkType)}>delete</button>
                        </span>)</>
                }
                <br/>
            </>)
            }
        </>}
        {rule.linkedWords.length > 0 && <>
            <h3>Related word</h3>
            {rule.linkedWords.map(lw => <>
                <WordLink word={lw.toWord} baseLanguage={rule.toLang}/>
                <SourceRefs source={lw.source} span={true}/>
                {allowEdit() && <>
                    &nbsp;(<span className="inlineButtonLink">
                            <button className="inlineButton" onClick={() => deleteLinkClicked(lw.toWord.id, lw.linkType)}>delete</button>
                        </span>)</>
                }
            </>)}
        </>}
        {rule.references.length > 0 && <>
            <h3>Referencing rules</h3>
            {rule.references.map(r => <>
                <RuleLink rule={r}/>
                <br/></>)}
        </>}
        {rule.referencingParadigms.length > 0 && <>
            <h3>Referencing paradigms</h3>
            {rule.referencingParadigms.map(p => <>
                <Link href={`/${graph}/paradigm/${p.id}`}>{p.name} ({p.refType})</Link>
            <br/></>)}
        </>}
        {rule.sequenceLinks.length > 0 && <>
            <h3>Sequences</h3>
            {rule.sequenceLinks.map(sl => <>
                {sl.sequenceName + ": "}
                {sl.prev && <>previous <RuleLink rule={sl.prev}/></>}
                {sl.prev && sl.next && ", "}
                {sl.next && <>next <RuleLink rule={sl.next}/></>}
                <br/>
            </>)}
        </>}
        {linkMode && <RuleLinkForm fromEntityId={rule.id} submitted={linkSubmitted} cancelled={() => setLinkMode(false)}/>}
        <p/>
        {allowEdit() && !showExampleForm &&
            <button onClick={() => {
                setShowExampleForm(true)
                setFocusTarget("exampleText")
            }}>Add Example</button>
        }
        {showExampleForm &&
            <EtymographForm
                 create={createExample}
                 submitted={exampleSubmitted}
                 cancelled={() => setShowExampleForm(false)}
                 defaultValues={{exampleSource: lastExampleSource}}
                 focusTarget={focusTarget}
                 setFocusTarget={setFocusTarget}>
                <table><tbody>
                    <FormRow id="exampleText" label="Example" size={50} inputAssist={true}/>
                    <FormRow id="exampleSource" label="Source"/>
                </tbody></table>
            </EtymographForm>
        }
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        {exampleUnmatched.length > 0 && <>
            <p>Unmatched example: {exampleUnmatched.map((w, i) => <>
                {i > 0 && ' > '}
                <WordLink word={w} baseLanguage={rule.toLang}/>
            </>)}</p>
        </>}
        {allowEdit() && !showTraceForm &&
            <>
                {' '}<button onClick={() => prepareTrace(false)}>Trace</button>
                {' '}<button onClick={() => prepareTrace(true)}>Trace Reverse</button>
            </>
        }
        {showTraceForm && <p>
            <EtymographForm<TraceFormData, RuleTraceResult>
                defaultValues={{traceLanguage: rule.toLang}}
                create={runTrace}
                submitted={showTrace}
                cancelled={() => setShowTraceForm(false)}
            >
                <table><tbody>
                    <LanguageSelect id="traceLanguage" label="Language"/>
                    <FormRow id="traceWord" label="Word"/>
                </tbody></table>
            </EtymographForm>
            <div className="ruleTrace">{traceResult}</div>
        </p>}
    </>
}
