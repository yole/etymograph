import RuleSequenceForm from "@/components/RuleSequenceForm";
import {useRouter} from "next/router";

export default function RuleSequenceEditor() {
    const router = useRouter()

    function submitted(toLang) {
        router.push("/rules/" + toLang)
    }

    return <RuleSequenceForm submitted={submitted}/>
}
