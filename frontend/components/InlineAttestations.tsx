import Link from "next/link";
import {AttestationViewModel} from "@/models";
import WordTextView from "@/components/WordTextView";

export default function InlineAttestations({attestations, graph, showPrefix = true, showWord = true}: {
    attestations: AttestationViewModel[], graph: string, showPrefix?: boolean, showWord?: boolean
}) {
    return <span>
        {showPrefix && <>{' '} in </>}{attestations.map((att, i) => <span key={`${att.textId}-${i}`}>
            {i > 0 && ", "}
            <Link href={`/${graph}/corpus/text/${att.textId}`}>{att.textTitle}</Link>
            {att.lineNumbers?.length > 0 && <> {att.lineNumbers.join(", ")}</>}
            {showWord && att.word && <>{' ('}<WordTextView text={att.word} syllabograms={att.syllabogramSequence}/>{')'}</>}
        </span>)}
    </span>
}
