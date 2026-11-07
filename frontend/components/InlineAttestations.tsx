import Link from "next/link";
import {AttestationViewModel} from "@/models";
import WordTextView from "@/components/WordTextView";

export default function InlineAttestations({attestations, graph}: {attestations: AttestationViewModel[], graph: string}) {
    return <span>
        {' '} in {attestations.map((att, i) => <span key={`${att.textId}-${i}`}>
            {i > 0 && ", "}
            <Link href={`/${graph}/corpus/text/${att.textId}`}>{att.textTitle}</Link>
            {att.lineNumbers?.length > 0 && <> {att.lineNumbers.join(", ")}</>}
            {att.word && <>{' ('}<WordTextView text={att.word} syllabograms={att.syllabogramSequence}/>{')'}</>}
        </span>)}
    </span>
}
