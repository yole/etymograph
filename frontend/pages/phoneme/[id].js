import {allowEdit, deletePhoneme, fetchBackend} from "@/api";
import {useEffect, useState} from "react";
import Link from "next/link";
import {useRouter} from "next/router";
import PhonemeForm from "@/components/PhonemeForm";
import SourceRefs from "@/components/SourceRefs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(`phoneme/${context.params.id}`)
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`phonemes`)
    const paths = props.loaderData.map(p => ({params: {id: p.id.toString()}}))
    return {paths, fallback: allowEdit()}
}

export default function Phoneme(props) {
    const phoneme = props.loaderData
    const [editMode, setEditMode] = useState(false)
    const router = useRouter()

    useEffect(() => { document.title = `Etymograph : ${phoneme.languageFullName} : Phoneme` })

    function submitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    function deletePhonemeClicked() {
        if (window.confirm("Delete this phoneme?")) {
            deletePhoneme(phoneme.id)
                .then(() => router.push("/language/" + phoneme.languageShortName))
        }
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/language/${phoneme.languageShortName}`}>{phoneme.languageFullName}</Link> {'> '}
            </small>
            Phoneme
        </h2>

        {!editMode && <p>
            Graphemes: {phoneme.graphemes.join(", ")}<br/>
            {phoneme.sound.length > 0 && <>Sound: {phoneme.sound}<br/></>}
            {phoneme.classes.length > 0 && "Classes: " + phoneme.classes}
            <SourceRefs source={phoneme.source}/>
            {phoneme.notes != null && <>
                <h3>Notes</h3>
                <p>{phoneme.notes}</p>
            </>}

            </p>}
            {editMode && <PhonemeForm
                updateId={phoneme.id}
                initialGraphemes={phoneme.graphemes.join(", ")}
                initialSound={phoneme.sound}
                initialClasses={phoneme.classes}
                initialSource={phoneme.sourceEditableText}
                submitted={submitted}
            />}

        {allowEdit() && <>
            <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
            {' '}
            <button onClick={() => deletePhonemeClicked()}>{"Delete"}</button>
        </>}
    </>
}
