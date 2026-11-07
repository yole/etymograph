import {useState} from "react";
import {useRouter} from "next/router";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faEdit, faTrash} from "@fortawesome/free-solid-svg-icons";
import {callApiAndRefresh, deleteOpenCompound, useAllowEditGraph} from "@/api";
import OpenCompoundModal from "@/components/OpenCompoundModal";
import WordLink from "@/components/WordLink";
import WordTextView, {wordTextToPlainText} from "@/components/WordTextView";
import SourceRefs from "@/components/SourceRefs";
import InlineAttestations from "@/components/InlineAttestations";
import {OpenCompoundViewModel, WordViewModel} from "@/models";

export default function OpenCompoundEntry({compound, word}: {compound: OpenCompoundViewModel, word: WordViewModel}) {
    const router = useRouter()
    const graph = router.query.graph as string
    const canEdit = useAllowEditGraph()
    const [editing, setEditing] = useState(false)
    const [deleting, setDeleting] = useState(false)
    const [errorText, setErrorText] = useState("")

    async function deleteClicked() {
        if (deleting || !window.confirm("Delete this open compound?")) return
        setDeleting(true)
        setErrorText("")
        try {
            await callApiAndRefresh(() => deleteOpenCompound(graph, compound.id), router, setErrorText)
        }
        catch (error) {
            setErrorText(error instanceof Error ? error.message : "Failed to delete open compound")
        }
        finally {
            setDeleting(false)
        }
    }

    return <div>
        {compound.components.map((component, index) => <span key={`${component.id}-${index}`}>
            {index > 0 && " "}
            {index === compound.headIndex
                ? <WordTextView text={`${Array.from(wordTextToPlainText(component.text, component.syllabogramSequence))[0]}.`}/>
                : <WordLink word={component} baseLanguage={word.language}/>}
        </span>)}
        {(compound.fullGloss || compound.gloss) && <>{' '}&apos;{compound.fullGloss || compound.gloss}&apos;</>}
        {compound.attestations?.length > 0 && <InlineAttestations attestations={compound.attestations} graph={graph}/>}
        {compound.notes && <> &ndash; {compound.notes}</>}
        <SourceRefs source={compound.source} span={true}/>
        {canEdit && <>
            {' '}<button type="button" className="inlineButton compoundComponentIcon" aria-label="Edit open compound" title="Edit"
                        disabled={deleting} onClick={() => setEditing(true)}>
                <FontAwesomeIcon icon={faEdit}/>
            </button>
            {' '}<button type="button" className="inlineButton compoundComponentIcon" aria-label="Delete open compound" title="Delete"
                        disabled={deleting} onClick={deleteClicked}>
                <FontAwesomeIcon icon={faTrash}/>
            </button>
            {editing && <OpenCompoundModal opened={true} headWord={word} compound={compound}
                onClose={() => setEditing(false)} onSaved={() => {
                    setEditing(false)
                    router.replace(router.asPath)
                }}/>
            }
        </>}
        {errorText !== "" && <div className="errorText" role="alert">{errorText}</div>}
    </div>
}
