import {useEffect, useState} from "react";
import {
    addRuleLink,
    addWordSequence,
    allowEdit,
    deleteLink,
    deleteRule,
    deleteWord,
    fetchAllGraphs, fetchPathsForAllGraphs,
    updateRule
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

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rule/${context.params.id}`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("rules", (r) => ({id: r.id.toString()}))
}

export default function Rule(params) {
    const rule = params.loaderData
    const [editMode, setEditMode] = useState(false)
    const [linkMode, setLinkMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    const [showExampleForm, setShowExampleForm] = useState(false)
    const [exampleText, setExampleText] = useState("")
    const [exampleSource, setExampleSource] = useState("")
    const [exampleUnmatched, setExampleUnmatched] = useState([])
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

    function deleteLinkClicked(entityId, linkType) {
        if (window.confirm("Delete this link?")) {
            deleteLink(graph, entityId, rule.id, linkType)
                .then(r => {
                    if (r.status === 200) {
                        router.replace(router.asPath)
                    }
                    else {
                        r.json().then(r => setErrorText(r.message))
                    }
                })
        }
    }

    function exampleSubmitted() {
        addWordSequence(graph, exampleText, exampleSource)
            .then(r => {
                if (r.status === 200) {
                    r.json().then(r => {
                        setShowExampleForm(false)
                        setExampleText("")
                        if (r.ruleIds.indexOf(rule.id) >= 0) {
                            setErrorText("")
                            setExampleUnmatched([])
                            router.replace(router.asPath)
                        }
                        else {
                            setErrorText("Example does not match rule")
                            setExampleUnmatched(r.words)
                        }
                    })
                }
                else {
                    r.json().then(r => setErrorText(r.message))
                }
            })

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
                {b.conditions.fragments.length > 0 && <div><RichText richText={b.conditions}/>:</div>}
                <ul>
                    {b.instructions.map(i => <li><RichText richText={i}></RichText></li>)}
                </ul>
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
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        {rule.examples.length > 0 && <>
            <h3>Examples</h3>
            <ul>
                {rule.examples.map(ex => <li>
                    <WordLink word={ex.toWord} baseLanguage={ex.fromWord.language}/>
                    {ex.toWord.gloss && ` "${ex.toWord.gloss}"`}
                    &nbsp;&rarr;&nbsp;
                    <WordLink word={ex.fromWord}/>
                    {ex.allRules.length > 1 && ex.ruleResults.length === 0 && <>
                        {' '}(
                        {ex.allRules.map((rl, i) => <>
                            {i > 0 && ", "}
                            {rl.toRuleId !== rule.id && <Link href={`/${graph}/rule/${rl.toRuleId}`}>{rl.toRuleName}</Link>}
                            {rl.toRuleId === rule.id && rl.toRuleName}
                        </>)}
                        )
                    </>}
                    {ex.allRules.length > 1 && ex.ruleResults.length > 0 && <>
                        {' '}(
                        {ex.toWord.text}
                        {ex.allRules.map((rl, i) => <>
                            {' '}<Link href={`/${graph}/rule/${rl.toRuleId}`} title={rl.toRuleName}>&gt;</Link>{' '}
                            {rl.toRuleId === rule.id && <b>{ex.ruleResults[i]}</b>}
                            {rl.toRuleId !== rule.id && ex.ruleResults[i]}
                        </>)}
                        )
                    </>}
                    {ex.expectedWord !== null && ex.expectedWord !== ex.fromWord && " [expected: " + ex.expectedWord + "]"}
                </li>)}
            </ul>
        </>}
        {allowEdit() && <button onClick={() => setShowExampleForm(!showExampleForm)}>Add example</button>}
        {showExampleForm && <>
            <table><tbody>
            <tr>
                <td>Example:</td>
                <td><input type="text" size="50" value={exampleText} onChange={(e) => setExampleText(e.target.value)}/></td>
            </tr>
            <tr>
                <td>Source:</td>
                <td><input type="text" value={exampleSource} onChange={(e) => setExampleSource(e.target.value)}/></td>
            </tr>
            </tbody></table>
            <button onClick={() => exampleSubmitted()}>Submit</button>
            {exampleUnmatched.length > 0 && <>
                <p>Unmatched example: {exampleUnmatched.map((w, i) => <>
                    {i > 0 && ' > '}
                    <WordLink word={w} baseLanguage={rule.toLang}/>
                </>)}</p>
            </>}
        </>}
    </>
}