import {useEffect, useState} from "react";
import {addRuleLink, allowEdit, deleteRule, deleteWord, updateRule} from "@/api";
import WordLink from "@/components/WordLink";
import {fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import RuleForm from "@/components/RuleForm";
import SourceRefs from "@/components/SourceRefs";
import RichText from "@/components/RichText";
import RuleLinkForm from "@/components/RuleLinkForm";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(`rule/${context.params.id}`)
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`rules`)
    const paths = props.loaderData.map(rule => ({params: {id: rule.id.toString()}}))
    return {paths, fallback: allowEdit()}
}

export default function Rule(params) {
    const rule = params.loaderData
    const [editMode, setEditMode] = useState(false)
    const [linkMode, setLinkMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    useEffect(() => { document.title = "Etymograph : Rule " + rule.name })

    function linkSubmitted(status, jr) {
        if (status === 200) {
            setErrorText("")
            setLinkMode(false)
            router.replace(router.asPath)
        } else {
            setErrorText(jr.message.length > 0 ? jr.message : "Failed to save rule")
        }
    }

    function submitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    function deleteRuleClicked() {
        if (window.confirm("Delete this rule?")) {
            deleteRule(rule.id)
                .then(() => router.push("/rules/" + rule.toLang))
        }
    }

    return <>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${rule.toLang}`}>{rule.toLangFullName}</Link> {'> '}
            <Link href={`/rules/${rule.toLang}`}>Rules</Link> {'> '}</small>
            {rule.name}</h2>
        {rule.fromLang !== rule.toLang && <p>From {rule.fromLangFullName} to {rule.toLangFullName}</p>}
        {rule.paradigmId !== null && <p>Paradigm: <Link href={`/paradigm/${rule.paradigmId}`}>{rule.paradigmName}</Link></p>}
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
            initialName={rule.name}
            initialFromLanguage={rule.fromLang}
            initialToLanguage={rule.toLang}
            initialAddedCategories={rule.addedCategories}
            initialReplacedCategories={rule.replacedCategories}
            initialFromPOS={rule.fromPOS}
            initialToPOS={rule.toPOS}
            initialSource={rule.sourceEditableText}
            initialNotes={rule.notes}
            initialEditableText={rule.editableText}
            submitted={submitted}
        />}

        {allowEdit() && <>
            <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>{' '}
            <button onClick={() => setLinkMode(!linkMode)}>{linkMode ? "Cancel" : "Add Link"}</button>{' '}
            <button onClick={() => deleteRuleClicked()}>{"Delete"}</button>
        </>}
        {rule.links.length > 0 && <>
            <h3>Related rules</h3>
            {rule.links.map(rl => <>
                <Link href={`/rule/${rl.toRuleId}`}>{rl.toRuleName}</Link>
                <br/></>)
            }
        </>}
        {rule.linkedWords.length > 0 && <>
            <h3>Related word</h3>
            {rule.linkedWords.map(lw => <>
                <WordLink word={lw.toWord}/>
                <SourceRefs source={lw.source}/>
            </>)}
        </>}
        {linkMode && <RuleLinkForm fromEntityId={rule.id} submitted={linkSubmitted} />}
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        {rule.examples.length > 0 && <>
            <h3>Examples</h3>
            <ul>
                {rule.examples.map(ex => <li>
                    {ex.fromWord.language !== ex.toWord.language && `${ex.toWord.language} `}
                    <WordLink word={ex.toWord}/>
                    {ex.toWord.gloss && ` "${ex.toWord.gloss}"`}
                    &nbsp;&rarr;&nbsp;
                    <WordLink word={ex.fromWord}/>
                    {ex.allRules.length > 1 && <>
                        {' '}(
                        {ex.allRules.map((rl, i) => <>
                            {i > 0 && ", "}
                            {rl.toRuleId !== rule.id && <Link href={`/rule/${rl.toRuleId}`}>{rl.toRuleName}</Link>}
                            {rl.toRuleId === rule.id && rl.toRuleName}
                        </>)}
                        )
                    </>}
                    {ex.expectedWord !== null && ex.expectedWord !== ex.fromWord && " [expected: " + ex.expectedWord + "]"}
                </li>)}
            </ul>
        </>}
    </>
}
