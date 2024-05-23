import {useContext, useState} from "react";
import {
    addWordSequence,
    allowEdit,
    deleteLink,
    deleteRule,
    fetchPathsForAllGraphs
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

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rule/${context.params.id}`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("rules", (r) => ({id: r.id.toString()}))
}

function ExampleList(params) {
    const graph = useContext(GraphContext)
    const rule = params.rule
    return <table className="tableWithBorders">
        <thead>
        <tr>
            <th>From</th>
            <th>To</th>
            <th>Gloss</th>
            <th>Steps</th>
            <th>Expected</th>
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
            <td>
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
            </td>
            <td>
                {ex.expectedWord !== null && ex.expectedWord !== ex.fromWord && ex.expectedWord}
            </td>
        </tr>)}
        </tbody>
    </table>
}

export default function Rule(params) {
    const rule = params.loaderData
    const [editMode, setEditMode] = useState(false)
    const [linkMode, setLinkMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    const [lastExampleSource, setLastExampleSource] = useState("")
    const [showExampleForm, setShowExampleForm] = useState(false)
    const [exampleUnmatched, setExampleUnmatched] = useState([])
    const [focusTarget, setFocusTarget] = useState(null)
    const router = useRouter()
    const graph = router.query.graph

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

    async function deleteLinkClicked(entityId, linkType) {
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

    return <>
    <Breadcrumbs langId={rule.toLang} langName={rule.toLangFullName}
                     steps={[{title: "Rules", url: `/${graph}/rules/${rule.toLang}`}]}
                     title={rule.name}/>
        {rule.fromLang !== rule.toLang && <p>From {rule.fromLangFullName} to {rule.toLangFullName}</p>}
        {rule.paradigmId !== null && <p>Paradigm: <Link href={`/${graph}/paradigm/${rule.paradigmId}`}>{rule.paradigmName}</Link></p>}
        {!editMode && <>
            {rule.fromPOS && rule.fromPOS === rule.toPOS && <p>POS: {rule.fromPOS}</p>}
            {rule.fromPOS && rule.fromPOS !== rule.toPOS && <p>From POS: {rule.fromPOS}</p>}
            {rule.toPOS && rule.fromPOS !== rule.toPOS && <p>To POS: {rule.toPOS}</p>}
            {rule.addedCategories && <p>Added category values: {rule.addedCategories} ({rule.addedCategoryDisplayNames})</p>}
            {rule.replacedCategories && <p>Replaced category values: {rule.replacedCategories}</p>}
            <SourceRefs source={rule.source}/>
            <p/>
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
            {rule.notes != null && <>
                <h3>Notes</h3>
                <p>{rule.notes}</p>
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
                fromPOS: rule.fromPOS,
                toPOS: rule.toPOS,
                source: rule.sourceEditableText,
                notes: rule.notes,
                text: rule.editableText
            }}
            submitted={submitted}
            globalState={params.globalState}
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
        {linkMode && <RuleLinkForm fromEntityId={rule.id} submitted={linkSubmitted} cancelled={() => setLinkMode(false)}/>}
        {rule.orphanExamples.length > 0 && <>
            <h3>Orphan Examples</h3>
            <ExampleList rule={rule} examples={rule.orphanExamples}/>
        </>}
        <p/>
        {allowEdit() && !showExampleForm &&
            <button onClick={() => {
                setShowExampleForm(true)
                setFocusTarget("exampleText")
            }}>Add example</button>
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
                    <FormRow id="exampleText" label="Example" size="50" inputAssist={true}/>
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
    </>
}
