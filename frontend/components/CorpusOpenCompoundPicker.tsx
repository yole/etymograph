import {useEffect, useState} from "react";
import {Modal, Stack, Text} from "@mantine/core";
import {useRouter} from "next/router";
import {associateCorpusOpenCompound, fetchCorpusOpenCompoundCandidates} from "@/api";
import WordTextView from "@/components/WordTextView";
import WordGloss from "@/components/WordGloss";
import {OpenCompoundViewModel} from "@/models";

export default function CorpusOpenCompoundPicker({corpusTextId, index, onClose}: {
    corpusTextId: number, index: number, onClose: () => void
}) {
    const router = useRouter()
    const graph = router.query.graph as string
    const [candidates, setCandidates] = useState<OpenCompoundViewModel[]>([])
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [errorText, setErrorText] = useState("")

    useEffect(() => {
        let cancelled = false
        setLoading(true)
        setErrorText("")
        fetchCorpusOpenCompoundCandidates(graph, corpusTextId, index).then(result => {
            if (!cancelled) setCandidates(result)
        }).catch(error => {
            if (!cancelled) setErrorText(error instanceof Error ? error.message : "Failed to load open compounds")
        }).finally(() => {
            if (!cancelled) setLoading(false)
        })
        return () => { cancelled = true }
    }, [graph, corpusTextId, index])

    async function linkCompound(compoundId: number) {
        if (saving) return
        setSaving(true)
        setErrorText("")
        try {
            const response = await associateCorpusOpenCompound(graph, corpusTextId, index, compoundId)
            if (!response.ok) {
                const error = await response.json()
                setErrorText(error.message || "Failed to link open compound")
                return
            }
            onClose()
            void router.replace(router.asPath)
        }
        catch (error) {
            setErrorText(error instanceof Error ? error.message : "Failed to link open compound")
        }
        finally {
            setSaving(false)
        }
    }

    return <Modal opened={true} onClose={onClose} title="Link to open compound" size="lg">
        <Stack gap="xs">
            {loading && <Text size="sm">Loading…</Text>}
            {!loading && !errorText && candidates.length === 0 && <Text size="sm">No matching open compounds</Text>}
            {!loading && candidates.map(compound => <button key={compound.id} type="button" className="wordPickerItem"
                disabled={saving} onClick={() => linkCompound(compound.id)}>
                {compound.components.map((word, i) => <span key={`${word.id}-${i}`}>
                    {i > 0 && " "}
                    <WordTextView text={word.text} syllabograms={word.syllabogramSequence}/>
                </span>)}
                {compound.gloss && <>{' '}&apos;<WordGloss gloss={compound.gloss}/>&apos;</>}
            </button>)}
            {errorText && <div className="errorText" role="alert">{errorText}</div>}
        </Stack>
    </Modal>
}
