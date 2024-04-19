import {allowEdit, deletePhoneme, fetchBackend, fetchPathsForAllGraphs} from "@/api";
import {useState} from "react";
import Link from "next/link";
import {useRouter} from "next/router";
import PhonemeForm from "@/forms/PhonemeForm";
import SourceRefs from "@/components/SourceRefs";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `phoneme/${context.params.id}`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("phonemes", (p) => ({id: p.id.toString()}))
}

export default function Phoneme(props) {
    const phoneme = props.loaderData
    const [editMode, setEditMode] = useState(false)
    const router = useRouter()
    const graph = router.query.graph

    function submitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    function deletePhonemeClicked() {
        if (window.confirm("Delete this phoneme?")) {
            deletePhoneme(graph, phoneme.id)
                .then(() => router.push(`/${graph}/language/` + phoneme.languageShortName))
        }
    }

    return <>
        <Breadcrumbs langId={phoneme.languageShortName} langName={phoneme.languageFullName} title="Phoneme"/>

        {!editMode && <>
            <p>
            Graphemes: {phoneme.graphemes.join(", ")}<br/>
            {phoneme.sound.length > 0 && <>Sound: {phoneme.sound}<br/></>}
            {phoneme.classes.length > 0 && "Classes: " + phoneme.classes}<br/>
            {phoneme.historical && "Historical"}
            </p>
            <SourceRefs source={phoneme.source}/>
            {phoneme.notes != null && <>
                <h3>Notes</h3>
                <p>{phoneme.notes}</p>
            </>}
        </>}
        {editMode && <PhonemeForm
            updateId={phoneme.id}
            defaultValues={{
                graphemes: phoneme.graphemes.join(", "),
                sound: phoneme.sound,
                classes: phoneme.classes,
                historical: phoneme.historical,
                source: phoneme.sourceEditableText
            }}
            submitted={submitted}
            cancelled={() => setEditMode(false)}
        />}

        {allowEdit() && <>
            {!editMode && <button onClick={() => setEditMode(true)}>Edit</button>}
            {' '}
            <button onClick={() => deletePhonemeClicked()}>Delete</button>
        </>}

        {phoneme.relatedRules.length > 0 && <>
            <h3>Related rules</h3>
            {phoneme.relatedRules.map(rg => <>
                <h4>{rg.title}</h4>
                {rg.rules.map(rr =>
                    <li>
                    <Link href={`/${graph}/rule/${rr.id}`}>{rr.name}</Link>
                    {rr.summary && <>: {rr.summary}</>}
                </li>)}
            </>)}
        </>}
    </>
}
