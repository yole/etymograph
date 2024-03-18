import RuleSequenceForm from "@/components/RuleSequenceForm";
import {useRouter} from "next/router";
import Link from "next/link";

export default function RuleSequenceEditor() {
    const router = useRouter()

    function submitted(toLang) {
        router.push("/rules/" + toLang)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
            </small>
            New Rule Sequence
        </h2>
        <RuleSequenceForm submitted={submitted}/>
    </>
}
