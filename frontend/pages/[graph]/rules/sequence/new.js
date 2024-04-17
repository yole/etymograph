import RuleSequenceForm from "@/forms/RuleSequenceForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useRouter} from "next/router";

export default function RuleSequenceEditor() {
    const router = useRouter()
    return <>
        <Breadcrumbs title="New Rule Sequence"/>
        <RuleSequenceForm redirectOnCreate={(data) => `/${router.query.graph}/rules/${data.toLang}`}/>
    </>
}
