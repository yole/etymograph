import {AttestationViewModel, WordViewModel} from "@/models";
import InlineAttestations from "@/components/InlineAttestations";
import WordTextView from "@/components/WordTextView";

export function groupAttestationsByForm(attestations: AttestationViewModel[], wordText: string): AttestationViewModel[][] {
    const groups = new Map<string, Map<number, AttestationViewModel>>()
    for (const attestation of attestations) {
        const text = attestation.word ?? wordText
        if (!groups.has(text)) groups.set(text, new Map())
        const texts = groups.get(text)
        const previous = texts.get(attestation.textId)
        texts.set(attestation.textId, previous ? {
            ...previous,
            lineNumbers: [...new Set([...previous.lineNumbers, ...attestation.lineNumbers])]
        } : attestation)
    }
    return [...groups.values()].map(texts => [...texts.values()])
}

export default function AttestationsTable({word, graph}: {
    word: Pick<WordViewModel, "text" | "syllabogramSequence" | "languageSyllabographic" | "attestations">,
    graph: string
}) {
    return <table className="tableWithBorders">
        <caption>Attestations</caption>
        <thead><tr>
            <th scope="col">{word.languageSyllabographic ? "Transliteration" : "Attested form"}</th>
            <th scope="col">Locations</th>
        </tr></thead>
        <tbody>{groupAttestationsByForm(word.attestations, word.text).map(attestations => {
            const attestation = attestations[0]
            return <tr key={attestation.word ?? word.text}>
                <td><WordTextView text={attestation.word ?? word.text}
                                  syllabograms={attestation.syllabogramSequence ??
                                      (attestation.word ? undefined : word.syllabogramSequence)}/></td>
                <td><InlineAttestations attestations={attestations} graph={graph} showPrefix={false} showWord={false}/></td>
            </tr>
        })}</tbody>
    </table>
}
