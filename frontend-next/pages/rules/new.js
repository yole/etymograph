import RuleForm from "@/components/RuleForm";
import {useRouter} from "next/router";

export default function RuleEditor() {
    const router = useRouter()

    function submitted(id) {
        router.push("/rule/" + r.id)
    }

    return <RuleForm submitted={submitted}/>
}
